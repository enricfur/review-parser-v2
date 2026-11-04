package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceServiceTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/instagram/id389801252";

    private static final String OTHER_URL =
            "https://apps.apple.com/ru/app/other/id123456789";

    @Test
    void registersCollectsAndReturnsReviews() throws Exception {
        ParsedReview review = review("review-1", APP_URL);

        ReviewCollector collector =
                (sourceKey, url) -> List.of(review);

        SourceService service = new SourceService(
                new SourceRegistry(),
                collector
        );

        SourceRegistry.Source source =
                service.registerAndCollect(APP_URL);

        assertEquals(1L, source.id());
        assertEquals(APP_URL, source.url());
        assertEquals(List.of(source), service.findAllSources());
        assertEquals(List.of(review), service.findAllReviews());
        assertEquals(
                List.of(review),
                service.findReviewsBySource(source.id())
        );
    }

    @Test
    void separatesReviewsFromDifferentSources() throws Exception {
        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) ->
                        List.of(review(sourceKey, url))
        );

        SourceRegistry.Source first =
                service.registerAndCollect(APP_URL);

        SourceRegistry.Source second =
                service.registerAndCollect(OTHER_URL);

        assertEquals(2, service.findAllSources().size());
        assertEquals(2, service.findAllReviews().size());

        assertEquals(
                List.of(review("1", APP_URL)),
                service.findReviewsBySource(first.id())
        );

        assertEquals(
                List.of(review("2", OTHER_URL)),
                service.findReviewsBySource(second.id())
        );
    }

    @Test
    void repeatedRegistrationRefreshesReviews() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) ->
                        List.of(review(
                                "review-" + calls.incrementAndGet(),
                                url
                        ))
        );

        SourceRegistry.Source first =
                service.registerAndCollect(APP_URL);

        SourceRegistry.Source second =
                service.registerAndCollect(APP_URL);

        assertEquals(first, second);
        assertEquals(1, service.findAllSources().size());
        assertEquals(2, calls.get());

        assertEquals(
                List.of(review("review-2", APP_URL)),
                service.findReviewsBySource(first.id())
        );
    }

    @Test
    void keepsPreviousReviewsWhenRefreshFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) -> {
                    if (calls.incrementAndGet() == 1) {
                        return List.of(review("original", url));
                    }

                    throw new IOException("Simulated loading failure");
                }
        );

        service.registerAndCollect(APP_URL);

        assertThrows(
                IOException.class,
                () -> service.registerAndCollect(APP_URL)
        );

        assertEquals(
                List.of(review("original", APP_URL)),
                service.findAllReviews()
        );
    }

    @Test
    void rejectsUnknownSourceId() {
        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) -> List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findReviewsBySource(999)
        );

        assertTrue(service.findAllReviews().isEmpty());
    }

    @Test
    void collectsTwoSourcesInParallel() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch allowCompletion = new CountDownLatch(1);

        ReviewCollector collector = (sourceKey, url) -> {
            bothStarted.countDown();

            if (!allowCompletion.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Collection timed out");
            }

            return List.of(review(sourceKey, url));
        };

        SourceService service = new SourceService(
                new SourceRegistry(),
                collector
        );

        SourceRegistry.Source first =
                service.registerAndCollectAsync(APP_URL);

        SourceRegistry.Source second =
                service.registerAndCollectAsync(OTHER_URL);

        try {
            assertTrue(
                    bothStarted.await(5, TimeUnit.SECONDS),
                    "Both collection threads must start in parallel"
            );

            assertEquals(
                    "RUNNING",
                    service.findCollectionStatus(first.id())
            );

            assertEquals(
                    "RUNNING",
                    service.findCollectionStatus(second.id())
            );
        } finally {
            allowCompletion.countDown();
        }

        for (int attempt = 0; attempt < 250; attempt++) {
            if ("COMPLETED".equals(
                    service.findCollectionStatus(first.id()))
                    && "COMPLETED".equals(
                    service.findCollectionStatus(second.id()))) {
                break;
            }

            Thread.sleep(20);
        }

        assertEquals(
                "COMPLETED",
                service.findCollectionStatus(first.id())
        );

        assertEquals(
                "COMPLETED",
                service.findCollectionStatus(second.id())
        );

        assertEquals(2, service.findAllReviews().size());
    }

    @Test
    void doesNotStartDuplicateCollectionWhileRunning()
            throws Exception {

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        ReviewCollector collector = (sourceKey, url) -> {
            calls.incrementAndGet();
            started.countDown();

            if (!allowCompletion.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Collection timed out");
            }

            return List.of(review(sourceKey, url));
        };

        SourceService service = new SourceService(
                new SourceRegistry(),
                collector
        );

        SourceRegistry.Source first =
                service.registerAndCollectAsync(APP_URL);

        try {
            assertTrue(
                    started.await(5, TimeUnit.SECONDS),
                    "First collection must start"
            );

            SourceRegistry.Source second =
                    service.registerAndCollectAsync(APP_URL);

            assertEquals(first, second);
            assertEquals(1, service.findAllSources().size());
            assertEquals(1, calls.get());

            assertEquals(
                    "RUNNING",
                    service.findCollectionStatus(first.id())
            );
        } finally {
            allowCompletion.countDown();
        }

        for (int attempt = 0; attempt < 250; attempt++) {
            if ("COMPLETED".equals(
                    service.findCollectionStatus(first.id()))) {
                break;
            }

            Thread.sleep(20);
        }

        assertEquals(
                "COMPLETED",
                service.findCollectionStatus(first.id())
        );

        assertEquals(1, calls.get());
        assertEquals(1, service.findAllReviews().size());
    }

    @Test
    void keepsPreviousReviewsWhenAsyncRefreshFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) -> {
                    if (calls.incrementAndGet() == 1) {
                        return List.of(review("original", url));
                    }

                    throw new IOException("Simulated refresh failure");
                }
        );

        SourceRegistry.Source first =
                service.registerAndCollect(APP_URL);

        SourceRegistry.Source second =
                service.registerAndCollectAsync(APP_URL);

        assertEquals(first, second);

        for (int attempt = 0; attempt < 250; attempt++) {
            if ("FAILED".equals(
                    service.findCollectionStatus(first.id()))) {
                break;
            }

            Thread.sleep(20);
        }

        assertEquals(
                "FAILED",
                service.findCollectionStatus(first.id())
        );

        assertEquals(2, calls.get());

        assertEquals(
                List.of(review("original", APP_URL)),
                service.findReviewsBySource(first.id())
        );
    }

    private static ParsedReview review(
            String id,
            String url
    ) {
        return new ParsedReview(
                id,
                url,
                "Test author",
                "Test title",
                "Test content",
                new BigDecimal("5"),
                LocalDate.of(2026, 11, 3)
        );
    }
}
