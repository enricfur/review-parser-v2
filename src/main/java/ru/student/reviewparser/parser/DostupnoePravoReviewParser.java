package ru.student.reviewparser.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DostupnoePravoReviewParser {

    private static final String HOST = "dostupnoepravo.ru";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.uuuu");

    public ParsedReview parse(String html, String sourceUrl) {
        URI uri = validateUrl(sourceUrl);

        String safeUrl = uri.getScheme() + "://"
                + uri.getRawAuthority() + uri.getRawPath();

        if (html == null || html.isBlank()) {
            throw error(safeUrl, "review", "empty HTML");
        }

        Element document = Jsoup.parse(html);

        Element review = required(
                document,
                "#single-review-page-v2 .single-review-v3",
                safeUrl,
                "review"
        );

        String author = parseAuthor(review, safeUrl);

        String content = requiredText(
                review,
                ".review-text",
                safeUrl,
                "content"
        );

        String title = requiredText(
                review,
                "h1.title-reviews",
                safeUrl,
                "title"
        );

        BigDecimal rating = parseRating(review, safeUrl);
        LocalDate publishedAt = parseDate(review, safeUrl);

        return new ParsedReview(
                uri.getRawPath(),
                safeUrl,
                author,
                title,
                content,
                rating,
                publishedAt
        );
    }

    public ParsedReview parseCard(
            String cardHtml,
            String objectUrl
    ) {
        URI objectUri = validateObjectUrl(objectUrl);

        String safeObjectUrl = objectUri.getScheme() + "://"
                + objectUri.getRawAuthority()
                + objectUri.getRawPath();

        if (cardHtml == null || cardHtml.isBlank()) {
            throw error(safeObjectUrl, "review", "empty HTML");
        }

        Element document = Jsoup.parse(cardHtml);

        Element card = required(
                document,
                ".review-single[data-id]",
                safeObjectUrl,
                "review"
        );

        String id = card.attr("data-id").strip();

        if (!id.matches("[0-9]+")) {
            throw error(safeObjectUrl, "id", "invalid");
        }

        String cardUrl = safeObjectUrl + "#review-" + id;

        String author = parseAuthor(card, cardUrl);

        String content = requiredText(
                card,
                ".review-text",
                cardUrl,
                "content"
        );

        String rawTitle = requiredText(
                card,
                ".review-object-name",
                cardUrl,
                "title"
        );

        int separator = rawTitle.indexOf(':');

        String title = separator >= 0
                ? rawTitle.substring(separator + 1).strip()
                : rawTitle;

        if (title.isBlank()) {
            throw error(cardUrl, "title", "empty");
        }

        BigDecimal rating = parseRating(card, cardUrl);
        LocalDate publishedAt = parseDate(card, cardUrl);

        return new ParsedReview(
                objectUri.getRawPath() + "#review-" + id,
                cardUrl,
                author,
                title,
                content,
                rating,
                publishedAt
        );
    }

    private String parseAuthor(
            Element review,
            String sourceUrl
    ) {
        String rawAuthor = requiredText(
                review,
                ".client-name",
                sourceUrl,
                "author"
        );

        String author = rawAuthor
                .replaceFirst("^Автор отзыва:\\s*", "")
                .split("\\|", 2)[0]
                .strip();

        if (author.isBlank()) {
            throw error(sourceUrl, "author", "empty");
        }

        return author;
    }

    private BigDecimal parseRating(
            Element review,
            String sourceUrl
    ) {
        String rawRating = requiredText(
                review,
                ".review-rating-stars-number",
                sourceUrl,
                "rating"
        );

        try {
            return new BigDecimal(rawRating);
        } catch (NumberFormatException exception) {
            throw error(sourceUrl, "rating", "invalid number");
        }
    }

    private LocalDate parseDate(
            Element review,
            String sourceUrl
    ) {
        String rawDate = requiredText(
                review,
                ".review-date-city span",
                sourceUrl,
                "date"
        );

        try {
            return LocalDate.parse(rawDate, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw error(sourceUrl, "date", "invalid format");
        }
    }

    private URI validateUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl missing");
        }

        final URI uri;

        try {
            uri = URI.create(sourceUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sourceUrl invalid");
        }

        if (!isAllowedUri(uri)
                || uri.getRawPath() == null
                || !uri.getRawPath().matches(
                "/survey/[0-9]+-[a-zA-Z0-9-]+/?"
        )) {
            throw new IllegalArgumentException(
                    "Unsupported Dostupnoe Pravo review URL"
            );
        }

        return uri;
    }

    private URI validateObjectUrl(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
            throw new IllegalArgumentException("objectUrl missing");
        }

        final URI uri;

        try {
            uri = URI.create(objectUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("objectUrl invalid");
        }

        if (!isAllowedUri(uri)
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath() == null
                || !uri.getRawPath().matches(
                "/review/(?:companies|lawyers)/[0-9]+-[a-zA-Z0-9-]+/?"
        )) {
            throw new IllegalArgumentException(
                    "Unsupported Dostupnoe Pravo object URL"
            );
        }

        return uri;
    }

    private boolean isAllowedUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && HOST.equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getPort() == -1;
    }

    private Element required(
            Element parent,
            String selector,
            String sourceUrl,
            String field
    ) {
        Element element = parent.selectFirst(selector);

        if (element == null) {
            throw error(sourceUrl, field, "missing");
        }

        return element;
    }

    private String requiredText(
            Element parent,
            String selector,
            String sourceUrl,
            String field
    ) {
        String value = required(
                parent,
                selector,
                sourceUrl,
                field
        ).text().strip();

        if (value.isBlank()) {
            throw error(sourceUrl, field, "empty");
        }

        return value;
    }

    private IllegalArgumentException error(
            String sourceUrl,
            String field,
            String reason
    ) {
        return new IllegalArgumentException(
                "Review " + sourceUrl + ": " + field + " " + reason
        );
    }
}
