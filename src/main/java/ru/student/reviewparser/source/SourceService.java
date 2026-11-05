package ru.student.reviewparser.source;

import jakarta.annotation.PreDestroy;
import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.collector.ReviewStorage;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SourceService {

    private static final Logger LOGGER =
            Logger.getLogger(SourceService.class.getName());

    private final SourceRegistry registry;
    private final ReviewCollector collector;
    private final ReviewStorage reviewStorage;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final ExecutorStatusLogger statusLogger;

    private final Map<Long, List<ParsedReview>> reviewsBySource =
            new LinkedHashMap<>();

    private final Map<Long, String> collectionStatuses =
            new LinkedHashMap<>();

    private boolean closed;

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector
    ) {
        this(registry, collector, 0, null);
    }

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector,
            long scheduleIntervalSeconds
    ) {
        this(registry, collector, scheduleIntervalSeconds, null);
    }

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector,
            long scheduleIntervalSeconds,
            ReviewStorage reviewStorage
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.collector = Objects.requireNonNull(collector);
        this.reviewStorage = reviewStorage;

        if (scheduleIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                    "Schedule interval cannot be negative"
            );
        }

        if (reviewStorage != null) {
            try {
                Map<Long, List<ParsedReview>> restored =
                        reviewStorage.load();

                for (Map.Entry<Long, List<ParsedReview>> entry
                        : restored.entrySet()) {
                    reviewsBySource.put(
                            entry.getKey(),
                            List.copyOf(entry.getValue())
                    );
                }

            } catch (IOException exception) {
                throw new UncheckedIOException(
                        "Could not load saved reviews",
                        exception
                );
            }
        }

        AtomicInteger threadNumber = new AtomicInteger();

        this.executor = new ThreadPoolExecutor(
                4,
                4,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8),
                task -> new Thread(
                        task,
                        "review-worker-" + threadNumber.incrementAndGet()
                ),
                new ThreadPoolExecutor.AbortPolicy()
        );

        this.executor.allowCoreThreadTimeOut(true);

        if (scheduleIntervalSeconds > 0) {
            this.scheduler =
                    Executors.newSingleThreadScheduledExecutor(
                            task -> new Thread(
                                    task,
                                    "review-scheduler"
                            )
                    );

            scheduler.scheduleWithFixedDelay(
                    this::runScheduledCollections,
                    scheduleIntervalSeconds,
                    scheduleIntervalSeconds,
                    TimeUnit.SECONDS
            );
        } else {
            this.scheduler = null;
        }

        this.statusLogger = new ExecutorStatusLogger(executor);
        this.statusLogger.start();
    }

    private void runScheduledCollections() {
        try {
            for (SourceRegistry.Source source : registry.findAll()) {
                synchronized (this) {
                    if (closed) {
                        return;
                    }

                    if (isCollectionActive(source.id())) {
                        continue;
                    }
                }

                try {
                    registerAndCollectAsync(source.url());

                } catch (RejectedExecutionException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Scheduled collection rejected for source "
                                    + source.id(),
                            exception
                    );

                } catch (RuntimeException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Could not schedule source "
                                    + source.id(),
                            exception
                    );
                }
            }

        } catch (RuntimeException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Scheduled collection cycle failed",
                    exception
            );
        }
    }

    public SourceRegistry.Source registerAndCollect(
            String url
    ) throws IOException, InterruptedException {

        SourceRegistry.Source source = registry.register(url);

        synchronized (this) {
            if (closed) {
                throw new RejectedExecutionException(
                        "Collection service is shutting down"
                );
            }

            if (isCollectionActive(source.id())) {
                throw new IllegalStateException(
                        "Collection is already active for source "
                                + source.id()
                );
            }

            collectionStatuses.put(source.id(), "RUNNING");
        }

        try {
            collectAndStore(source);

            synchronized (this) {
                collectionStatuses.put(
                        source.id(),
                        closed ? "FAILED" : "COMPLETED"
                );
            }

        } catch (IOException | InterruptedException exception) {
            markFailed(source.id());
            throw exception;

        } catch (RuntimeException exception) {
            markFailed(source.id());
            throw exception;
        }

        return source;
    }

    public SourceRegistry.Source registerAndCollectAsync(
            String url
    ) {
        SourceRegistry.Source source = registry.register(url);

        synchronized (this) {
            if (closed) {
                throw new RejectedExecutionException(
                        "Collection service is shutting down"
                );
            }

            if (isCollectionActive(source.id())) {
                return source;
            }

            collectionStatuses.put(source.id(), "QUEUED");

            Runnable task = () -> {
                synchronized (this) {
                    if (closed) {
                        collectionStatuses.put(source.id(), "FAILED");
                        return;
                    }

                    collectionStatuses.put(source.id(), "RUNNING");
                }

                try {
                    collectAndStore(source);

                    synchronized (this) {
                        collectionStatuses.put(
                                source.id(),
                                closed ? "FAILED" : "COMPLETED"
                        );
                    }

                } catch (IOException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Collection failed for source " + source.id(),
                            exception
                    );
                    markFailed(source.id());

                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    markFailed(source.id());

                } catch (RuntimeException exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Collection failed for source " + source.id(),
                            exception
                    );
                    markFailed(source.id());
                }
            };

            try {
                executor.execute(task);
            } catch (RejectedExecutionException exception) {
                collectionStatuses.put(source.id(), "FAILED");
                throw exception;
            }
        }

        return source;
    }

    private boolean isCollectionActive(long sourceId) {
        String status = collectionStatuses.get(sourceId);

        return "QUEUED".equals(status)
                || "RUNNING".equals(status);
    }

    private void collectAndStore(
            SourceRegistry.Source source
    ) throws IOException, InterruptedException {

        List<ParsedReview> reviews =
                collector.collectFromObject(
                        Long.toString(source.id()),
                        source.url()
                );

        List<ParsedReview> snapshot = List.copyOf(reviews);

        synchronized (this) {
            if (closed) {
                return;
            }

            if (reviewStorage != null) {
                Map<Long, List<ParsedReview>> updated =
                        new LinkedHashMap<>(reviewsBySource);

                updated.put(source.id(), snapshot);

                reviewStorage.save(updated);
            }

            reviewsBySource.put(source.id(), snapshot);
        }
    }

    private synchronized void markFailed(long sourceId) {
        collectionStatuses.put(sourceId, "FAILED");
    }

    public synchronized String findCollectionStatus(long sourceId) {
        if (registry.findById(sourceId) == null) {
            throw new IllegalArgumentException(
                    "Unknown source ID: " + sourceId
            );
        }

        return collectionStatuses.get(sourceId);
    }

    public List<SourceRegistry.Source> findAllSources() {
        return registry.findAll();
    }

    public synchronized List<ParsedReview> findAllReviews() {
        List<ParsedReview> result = new ArrayList<>();

        for (List<ParsedReview> reviews : reviewsBySource.values()) {
            result.addAll(reviews);
        }

        return List.copyOf(result);
    }

    public synchronized List<ParsedReview> findReviewsBySource(
            long sourceId
    ) {
        if (registry.findById(sourceId) == null) {
            throw new IllegalArgumentException(
                    "Unknown source ID: " + sourceId
            );
        }

        return reviewsBySource.getOrDefault(
                sourceId,
                List.of()
        );
    }

    @PreDestroy
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }

            closed = true;

            statusLogger.close();

            if (scheduler != null) {
                scheduler.shutdownNow();
            }

            executor.shutdownNow();

            collectionStatuses.replaceAll((id, status) ->
                    "QUEUED".equals(status) || "RUNNING".equals(status)
                            ? "FAILED"
                            : status
            );
        }

        // Wait outside the synchronized block so workers
        // can acquire the service lock while finishing.
        try {
            if (scheduler != null
                    && !scheduler.awaitTermination(
                    2, TimeUnit.SECONDS
            )) {
                LOGGER.warning(
                        "Collection scheduler did not stop within 2 seconds"
                );
            }

            if (!executor.awaitTermination(
                    2, TimeUnit.SECONDS
            )) {
                LOGGER.warning(
                        "Collection executor did not stop within 2 seconds"
                );
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            LOGGER.warning(
                    "Interrupted while stopping collection service"
            );
        }
    }
}
