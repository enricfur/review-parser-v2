package ru.student.reviewparser.collector;

import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.parser.AppleFeedParser;
import ru.student.reviewparser.source.SourceUrlPolicy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleReviewCollector implements ReviewCollector {

    private static final Pattern APP_PATH = Pattern.compile(
            "^/([a-z]{2})/app/[^/]+/id([0-9]+)$"
    );

    private static final int MAX_PAGES = 3;
    private static final int MAX_ROUNDS = 3;

    private static final Duration DEFAULT_RETRY_PAUSE =
            Duration.ofSeconds(2);

    private final JsonLoader loader;
    private final AppleFeedParser parser;
    private final Duration retryPause;

    public AppleReviewCollector(
            JsonLoader loader,
            AppleFeedParser parser
    ) {
        this(loader, parser, DEFAULT_RETRY_PAUSE);
    }

    AppleReviewCollector(
            JsonLoader loader,
            AppleFeedParser parser,
            Duration retryPause
    ) {
        this.loader = Objects.requireNonNull(loader);
        this.parser = Objects.requireNonNull(parser);
        this.retryPause = Objects.requireNonNull(retryPause);

        if (retryPause.isNegative()) {
            throw new IllegalArgumentException(
                    "Retry pause must not be negative"
            );
        }
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

        URI appUri = SourceUrlPolicy
                .validateAndNormalize(objectUrl);

        if (!"apps.apple.com".equals(appUri.getHost())
                || appUri.getRawQuery() != null) {
            throw new IllegalArgumentException(
                    "Expected an App Store application URL"
            );
        }

        Matcher matcher = APP_PATH.matcher(appUri.getRawPath());

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid App Store application path"
            );
        }

        String country = matcher.group(1);
        String appId = matcher.group(2);

        String feedBase =
                "https://itunes.apple.com/"
                        + country
                        + "/rss/customerreviews/";

        String feedSuffix =
                "id=" + appId + "/sortby=mostrecent/json";

        String normalizedAppUrl = appUri.toASCIIString();

        Map<String, ParsedReview> uniqueReviews =
                new LinkedHashMap<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            List<String> feedUrls;

            if (page == 1) {
                feedUrls = List.of(
                        feedBase + feedSuffix,
                        feedBase + "page=1/" + feedSuffix
                );
            } else {
                feedUrls = List.of(
                        feedBase + "page=" + page + "/"
                                + feedSuffix
                );
            }

            List<ParsedReview> pageReviews =
                    loadReviews(feedUrls, normalizedAppUrl);

            for (ParsedReview review : pageReviews) {
                uniqueReviews.putIfAbsent(
                        review.externalId(),
                        review
                );
            }
        }

        return List.copyOf(uniqueReviews.values());
    }

    private List<ParsedReview> loadReviews(
            List<String> feedUrls,
            String appUrl
    ) throws IOException, InterruptedException {

        Exception lastFailure = null;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (round > 0) {
                Thread.sleep(retryPause.toMillis());
            }

            for (String feedUrl : feedUrls) {
                try {
                    List<ParsedReview> reviews =
                            parser.parse(
                                    loader.load(feedUrl),
                                    appUrl
                            );

                    if (!reviews.isEmpty()) {
                        return reviews;
                    }

                } catch (IOException | IllegalArgumentException exception) {
                    lastFailure = exception;
                }
            }
        }

        throw new IOException(
                "Apple feed returned no usable reviews after "
                        + MAX_ROUNDS
                        + " rounds",
                lastFailure
        );
    }
}
