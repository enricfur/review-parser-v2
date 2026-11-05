package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.collector.ReviewCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceServiceShutdownTest {

    @Test
    void shutdownCancelsQueuedTasksAndInterruptsRunningTasks()
            throws Exception {

        CountDownLatch fourStarted = new CountDownLatch(4);
        CountDownLatch releaseCollections = new CountDownLatch(1);

        AtomicInteger startedCount = new AtomicInteger();
        AtomicInteger interruptedCount = new AtomicInteger();

        ReviewCollector collector = (sourceKey, url) -> {
            startedCount.incrementAndGet();
            fourStarted.countDown();

            try {
                releaseCollections.await();
            } catch (InterruptedException exception) {
                interruptedCount.incrementAndGet();
                throw exception;
            }

            return List.of();
        };

        SourceService service = new SourceService(
                new SourceRegistry(),
                collector
        );

        List<SourceRegistry.Source> sources = new ArrayList<>();

        try {
            for (int i = 1; i <= 4; i++) {
                sources.add(
                        service.registerAndCollectAsync(url(i))
                );
            }

            assertTrue(
                    fourStarted.await(5, TimeUnit.SECONDS),
                    "Four worker tasks must start"
            );

            for (int i = 5; i <= 12; i++) {
                sources.add(
                        service.registerAndCollectAsync(url(i))
                );
            }

            for (int i = 4; i < 12; i++) {
                assertEquals(
                        "QUEUED",
                        service.findCollectionStatus(
                                sources.get(i).id()
                        )
                );
            }

            service.close();

            assertEquals(
                    4,
                    interruptedCount.get(),
                    "All four running tasks must be interrupted"
            );

            assertEquals(
                    4,
                    startedCount.get(),
                    "Queued tasks must not start"
            );

            for (SourceRegistry.Source source : sources) {
                assertEquals(
                        "FAILED",
                        service.findCollectionStatus(source.id()),
                        "Task must not remain QUEUED or RUNNING"
                );
            }

            assertThrows(
                    RejectedExecutionException.class,
                    () -> service.registerAndCollectAsync(url(13))
            );

            // A repeated shutdown must be safe.
            service.close();

        } finally {
            releaseCollections.countDown();
            service.close();
        }
    }

    private static String url(int number) {
        return "https://apps.apple.com/ru/app/test/id"
                + (200000000 + number);
    }
}
