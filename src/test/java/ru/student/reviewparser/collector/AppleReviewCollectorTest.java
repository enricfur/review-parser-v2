package ru.student.reviewparser.collector;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.parser.AppleFeedParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleReviewCollectorTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/instagram/id389801252";

    private static final String FEED_URL =
            "https://itunes.apple.com/ru/rss/customerreviews/id=389801252/sortby=mostrecent/json";

    private static final String PAGE_ONE_URL =
            "https://itunes.apple.com/ru/rss/customerreviews/page=1/id=389801252/sortby=mostrecent/json";

    private static final String PAGE_TWO_URL =
            "https://itunes.apple.com/ru/rss/customerreviews/page=2/id=389801252/sortby=mostrecent/json";

    private static final String PAGE_THREE_URL =
            "https://itunes.apple.com/ru/rss/customerreviews/page=3/id=389801252/sortby=mostrecent/json";

    private static final String JSON = """
            {
              "feed": {
                "entry": [
                  {
                    "id": {"label": "14724696479"},
                    "author": {"name": {"label": "(Марьям)"}},
                    "title": {"label": "Ну пожалуйста"},
                    "content": {"label": "Тестовый текст отзыва"},
                    "im:rating": {"label": "4"},
                    "updated": {"label": "2026-10-29T00:41:43-07:00"}
                  }
                ]
              }
            }
            """;

    private static final String EMPTY_ENTRIES_JSON = """
            {
              "feed": {
                "entry": []
              }
            }
            """;

    private static final String NO_ENTRY_FIELD_JSON = """
            {
              "feed": {
                "author": {"name": {"label": "iTunes Store"}}
              }
            }
            """;

    @Test
    void loadsAndParsesReviewsFromThreePages() throws Exception {
        List<String> requestedUrls = new ArrayList<>();

        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> {
                            requestedUrls.add(url);
                            return JSON;
                        },
                        new AppleFeedParser()
                );

        List<ParsedReview> reviews =
                collector.collectFromObject("42", APP_URL);

        assertEquals(
                List.of(FEED_URL, PAGE_TWO_URL, PAGE_THREE_URL),
                requestedUrls
        );

        // The same review appears on all three pages.
        // It must be present only once.
        assertEquals(1, reviews.size());

        ParsedReview review = reviews.get(0);

        assertEquals("14724696479", review.externalId());
        assertEquals(APP_URL, review.sourceUrl());
        assertEquals("(Марьям)", review.author());
        assertEquals("Ну пожалуйста", review.title());
        assertEquals("Тестовый текст отзыва", review.content());
        assertEquals(new BigDecimal("4"), review.rating());
        assertEquals(
                LocalDate.of(2026, 10, 29),
                review.publishedAt()
        );
    }

    @Test
    void fallsBackToPageOneWhenMainFeedIsInvalid()
            throws Exception {

        List<String> requestedUrls = new ArrayList<>();

        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> {
                            requestedUrls.add(url);

                            if (FEED_URL.equals(url)) {
                                return NO_ENTRY_FIELD_JSON;
                            }

                            return JSON;
                        },
                        new AppleFeedParser(),
                        Duration.ZERO
                );

        List<ParsedReview> reviews =
                collector.collectFromObject("42", APP_URL);

        assertEquals(
                List.of(
                        FEED_URL,
                        PAGE_ONE_URL,
                        PAGE_TWO_URL,
                        PAGE_THREE_URL
                ),
                requestedUrls
        );

        assertEquals(1, reviews.size());
    }

    @Test
    void retriesWhenFeedContainsNoUsableReviews() {
        AtomicInteger calls = new AtomicInteger();

        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> {
                            calls.incrementAndGet();
                            return NO_ENTRY_FIELD_JSON;
                        },
                        new AppleFeedParser(),
                        Duration.ZERO
                );

        assertThrows(
                IOException.class,
                () -> collector.collectFromObject("42", APP_URL)
        );

        // Three rounds, two URLs per round.
        assertEquals(6, calls.get());
    }

    @Test
    void rejectsNetworkFailures() {
        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> {
                            throw new IOException(
                                    "Simulated network failure"
                            );
                        },
                        new AppleFeedParser(),
                        Duration.ZERO
                );

        assertThrows(
                IOException.class,
                () -> collector.collectFromObject("42", APP_URL)
        );
    }

    @Test
    void rejectsEmptyFeed() {
        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> EMPTY_ENTRIES_JSON,
                        new AppleFeedParser(),
                        Duration.ZERO
                );

        assertThrows(
                IOException.class,
                () -> collector.collectFromObject("42", APP_URL)
        );
    }

    @Test
    void rejectsFeedWithoutEntryField() {
        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> NO_ENTRY_FIELD_JSON,
                        new AppleFeedParser(),
                        Duration.ZERO
                );

        assertThrows(
                IOException.class,
                () -> collector.collectFromObject("42", APP_URL)
        );
    }

    @Test
    void rejectsUnsupportedAppUrlsWithoutLoading() {
        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> {
                            throw new AssertionError(
                                    "Loader must not be called"
                            );
                        },
                        new AppleFeedParser()
                );

        for (String url : List.of(
                "https://example.test/ru/app/instagram/id389801252",
                "https://apps.apple.com/ru/app/instagram",
                "https://apps.apple.com/ru/app/instagram/idinvalid",
                "https://apps.apple.com/ru/app/instagram/id389801252/extra",
                "https://apps.apple.com/ru/app/instagram/id389801252?token=secret"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> collector.collectFromObject("42", url),
                    url
            );
        }
    }

    @Test
    void rejectsBlankSourceKey() {
        AppleReviewCollector collector =
                new AppleReviewCollector(
                        url -> JSON,
                        new AppleFeedParser()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> collector.collectFromObject("", APP_URL)
        );
    }
}
