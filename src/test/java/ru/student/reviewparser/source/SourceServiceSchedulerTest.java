package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.collector.ReviewCollector;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceServiceSchedulerTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/test/id123456789";

    @Test
    void automaticallyRefreshesRegisteredSource()
            throws Exception {

        AtomicInteger collectionCount = new AtomicInteger();
        CountDownLatch twoCollections = new CountDownLatch(2);

        ReviewCollector collector = (sourceKey, url) -> {
            collectionCount.incrementAndGet();
            twoCollections.countDown();
            return List.of();
        };

        SourceService service = new SourceService(
                new SourceRegistry(),
                collector,
                1
        );

        try {
            SourceRegistry.Source source =
                    service.registerAndCollectAsync(APP_URL);

            assertTrue(
                    twoCollections.await(5, TimeUnit.SECONDS),
                    "Scheduler must refresh the source automatically"
            );

            assertEquals(1, service.findAllSources().size());

            assertTrue(
                    collectionCount.get() >= 2,
                    "At least two collections must have occurred"
            );

            assertEquals(
                    source.id(),
                    service.findAllSources().get(0).id()
            );

        } finally {
            service.close();
        }
    }
}
