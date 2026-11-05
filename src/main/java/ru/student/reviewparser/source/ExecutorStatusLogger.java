package ru.student.reviewparser.source;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

public class ExecutorStatusLogger implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(ExecutorStatusLogger.class.getName());

    private final ThreadPoolExecutor executor;
    private final Thread thread;

    public ExecutorStatusLogger(ThreadPoolExecutor executor) {
        this.executor = Objects.requireNonNull(executor);

        this.thread = new Thread(
                this::logPeriodically,
                "review-status-logger"
        );

        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    private void logPeriodically() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(30_000);

                LOGGER.info(
                        "Review executor: active="
                                + executor.getActiveCount()
                                + ", queued="
                                + executor.getQueue().size()
                                + ", completed="
                                + executor.getCompletedTaskCount()
                );

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        thread.interrupt();
    }
}
