package ru.student.reviewparser.collector;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.parser.DostupnoePravoReviewParser;
import ru.student.reviewparser.source.SourceUrlPolicy;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DostupnoePravoReviewCollector implements ReviewCollector {

    private static final String HOST = "dostupnoepravo.ru";

    private static final int MAX_PAGES = 3;
    private static final long PAGE_PAUSE_MILLIS = 400;

    private static final String REVIEW_LINK_SELECTOR =
            "a.review-text-dark[href]";

    private static final String REVIEW_CARD_SELECTOR =
            ".review-single[data-id]";

    private static final String NEXT_PAGE_SELECTOR =
            "li.page-item.next:not(.disabled) a.page-link[href]";

    private final HtmlLoader loader;
    private final DostupnoePravoReviewParser parser;

    public DostupnoePravoReviewCollector(
            HtmlLoader loader,
            DostupnoePravoReviewParser parser
    ) {
        this.loader = Objects.requireNonNull(loader);
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public List<ParsedReview> collectFromObject(
            String sourceKey,
            String objectUrl
    ) throws IOException, InterruptedException {

        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Source key must not be blank"
            );
        }

        URI objectUri = validateObjectUrl(objectUrl);

        List<ParsedReview> allReviews = new ArrayList<>();
        Set<String> processedIds = new LinkedHashSet<>();

        URI currentPageUri = objectUri;

        for (int pageNumber = 1;
             pageNumber <= MAX_PAGES;
             pageNumber++) {

            String html = loader.load(
                    currentPageUri.toString()
            );

            Document document = parseObjectHtml(
                    html,
                    currentPageUri
            );

            collectPageReviews(
                    document,
                    objectUri,
                    currentPageUri,
                    processedIds,
                    allReviews
            );

            if (pageNumber == MAX_PAGES) {
                break;
            }

            URI nextPageUri = findNextPage(
                    document,
                    objectUri,
                    currentPageUri,
                    pageNumber + 1
            );

            if (nextPageUri == null) {
                break;
            }

            Thread.sleep(PAGE_PAUSE_MILLIS);
            currentPageUri = nextPageUri;
        }

        return List.copyOf(allReviews);
    }

    private Document parseObjectHtml(
            String html,
            URI pageUri
    ) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException(
                    "Dostupnoe Pravo object HTML is empty"
            );
        }

        return Jsoup.parse(
                html,
                pageUri.toString()
        );
    }

    private void collectPageReviews(
            Document document,
            URI objectUri,
            URI pageUri,
            Set<String> processedIds,
            List<ParsedReview> allReviews
    ) throws IOException, InterruptedException {

        List<Element> cards = document.select(
                REVIEW_CARD_SELECTOR
        );

        if (!cards.isEmpty()) {
            collectFromCards(
                    cards,
                    objectUri,
                    processedIds,
                    allReviews
            );
            return;
        }

        List<String> reviewUrls = extractReviewUrls(
                document,
                pageUri
        );

        for (String reviewUrl : reviewUrls) {
            String reviewHtml = loader.load(reviewUrl);

            ParsedReview review = parser.parse(
                    reviewHtml,
                    reviewUrl
            );

            if (processedIds.add(review.externalId())) {
                allReviews.add(review);
            }
        }
    }

    private void collectFromCards(
            List<Element> cards,
            URI objectUri,
            Set<String> processedIds,
            List<ParsedReview> allReviews
    ) throws IOException, InterruptedException {

        for (Element card : cards) {
            String id = card.attr("data-id").strip();

            if (!id.matches("[0-9]+")) {
                throw new IllegalArgumentException(
                        "Invalid Dostupnoe Pravo review ID"
                );
            }

            if (!processedIds.add(id)) {
                continue;
            }

            String reviewUrl = findReviewUrl(
                    card,
                    objectUri
            );

            ParsedReview review;

            if (reviewUrl != null) {
                String reviewHtml = loader.load(reviewUrl);

                review = parser.parse(
                        reviewHtml,
                        reviewUrl
                );
            } else {
                review = parser.parseCard(
                        card.outerHtml(),
                        objectUri.toString()
                );
            }

            allReviews.add(review);
        }
    }

    private URI findNextPage(
            Document document,
            URI objectUri,
            URI currentPageUri,
            int expectedPage
    ) {
        Element link = document.selectFirst(
                NEXT_PAGE_SELECTOR
        );

        if (link == null) {
            return null;
        }

        final URI nextUri;

        try {
            nextUri = currentPageUri.resolve(
                    link.attr("href").strip()
            ).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid Dostupnoe Pravo next page URL",
                    exception
            );
        }

        String expectedQuery = "page=" + expectedPage;

        if (!isAllowedUri(nextUri)
                || !Objects.equals(
                objectUri.getRawPath(),
                nextUri.getRawPath()
        )
                || !expectedQuery.equals(
                nextUri.getRawQuery()
        )
                || nextUri.getRawFragment() != null) {

            throw new IllegalArgumentException(
                    "Unsupported Dostupnoe Pravo next page URL"
            );
        }

        return nextUri;
    }

    private String findReviewUrl(
            Element card,
            URI objectUri
    ) {
        for (Element link : card.select(
                REVIEW_LINK_SELECTOR
        )) {
            String reviewUrl = resolveReviewUrl(
                    link.attr("href"),
                    objectUri
            );

            if (reviewUrl != null) {
                return reviewUrl;
            }
        }

        return null;
    }

    private List<String> extractReviewUrls(
            Document document,
            URI pageUri
    ) {
        Set<String> links = new LinkedHashSet<>();

        for (Element element : document.select(
                REVIEW_LINK_SELECTOR
        )) {
            String reviewUrl = resolveReviewUrl(
                    element.attr("href"),
                    pageUri
            );

            if (reviewUrl != null) {
                links.add(reviewUrl);
            }
        }

        if (links.isEmpty()) {
            throw new IllegalArgumentException(
                    "No supported Dostupnoe Pravo review links found"
            );
        }

        return List.copyOf(links);
    }

    private String resolveReviewUrl(
            String href,
            URI objectUri
    ) {
        if (href == null || href.isBlank()) {
            return null;
        }

        final URI resolved;

        try {
            resolved = objectUri.resolve(
                    href.strip()
            ).normalize();
        } catch (IllegalArgumentException exception) {
            return null;
        }

        if (!isReviewUrl(resolved)) {
            return null;
        }

        return "https://" + HOST
                + resolved.getRawPath();
    }

    private URI validateObjectUrl(String objectUrl) {
        URI uri = SourceUrlPolicy.validateAndNormalize(
                objectUrl
        );

        if (!isAllowedUri(uri)
                || uri.getRawQuery() != null
                || uri.getRawPath() == null
                || !uri.getRawPath().matches(
                "/review/(?:companies|lawyers)/"
                        + "[0-9]+-[a-zA-Z0-9-]+/?"
        )) {
            throw new IllegalArgumentException(
                    "Unsupported Dostupnoe Pravo object URL"
            );
        }

        return uri;
    }

    private boolean isReviewUrl(URI uri) {
        return isAllowedUri(uri)
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && uri.getRawPath() != null
                && uri.getRawPath().matches(
                "/survey/[0-9]+-[a-zA-Z0-9-]+/?"
        );
    }

    private boolean isAllowedUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && HOST.equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getPort() == -1
                && uri.getRawFragment() == null;
    }
}
