package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.collector.ReviewCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceServiceExecutorTest {

    @Test
    void limitsWorkersAndQueueAndRecoversAfterRejection()
            throws Exception {

        CountDownLatch fourStarted = new CountDownLatch(4);
        CountDownLatch allowCompletion = new CountDownLatch(1);

        ReviewCollector collector = (sourceKey, url) -> {
            fourStarted.countDown();

            if (!allowCompletion.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Collection timed out");
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

            for (int i = 5; i <= 12; i++) {
                assertEquals(
                        "QUEUED",
                        service.findCollectionStatus(
                                sources.get(i - 1).id()
                        )
                );
            }

            assertThrows(
                    RejectedExecutionException.class,
                    () -> service.registerAndCollectAsync(url(13))
            );

            SourceRegistry.Source rejected =
                    service.findAllSources().get(12);

            assertEquals(
                    "FAILED",
                    service.findCollectionStatus(rejected.id())
            );

        } finally {
            allowCompletion.countDown();
        }

        try {
            for (SourceRegistry.Source source : sources) {
                waitForCompleted(service, source.id());
            }

            assertEquals(13, service.findAllSources().size());

            SourceRegistry.Source rejected =
                    service.findAllSources().get(12);

            SourceRegistry.Source retried =
                    service.registerAndCollectAsync(rejected.url());

            assertEquals(rejected.id(), retried.id());
            waitForCompleted(service, retried.id());

        } finally {
            service.close();
        }
    }

    private static void waitForCompleted(
            SourceService service,
            long sourceId
    ) throws InterruptedException {

        for (int attempt = 0; attempt < 250; attempt++) {
            if ("COMPLETED".equals(
                    service.findCollectionStatus(sourceId))) {
                return;
            }

            Thread.sleep(20);
        }

        assertEquals(
                "COMPLETED",
                service.findCollectionStatus(sourceId)
        );
    }

    private static String url(int number) {
        return "https://apps.apple.com/ru/app/test/id"
                + (100000000 + number);
    }
}
