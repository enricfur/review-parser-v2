package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorStatusLoggerTest {

    @Test
    void startsDaemonThreadAndStopsOnClose()
            throws InterruptedException {

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1)
        );

        ExecutorStatusLogger logger =
                new ExecutorStatusLogger(executor);

        Set<Long> existingThreadIds =
                Thread.getAllStackTraces()
                        .keySet()
                        .stream()
                        .map(Thread::threadId)
                        .collect(Collectors.toSet());

        try {
            logger.start();

            Thread monitor = Thread.getAllStackTraces()
                    .keySet()
                    .stream()
                    .filter(thread ->
                            "review-status-logger".equals(thread.getName()))
                    .filter(thread ->
                            !existingThreadIds.contains(thread.threadId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(
                    monitor,
                    "Logger must start a new thread"
            );

            assertTrue(
                    monitor.isDaemon(),
                    "Logger thread must be a daemon"
            );

            assertTrue(
                    monitor.isAlive(),
                    "Logger thread must be running"
            );

            logger.close();

            monitor.join(2000);

            assertFalse(
                    monitor.isAlive(),
                    "Logger thread must stop after close"
            );

        } finally {
            logger.close();
            executor.shutdownNow();
        }
    }
}
