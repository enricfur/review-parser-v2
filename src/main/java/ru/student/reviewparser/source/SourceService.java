package ru.student.reviewparser.source;

import jakarta.annotation.PreDestroy;
import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
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
    private final ThreadPoolExecutor executor;

    private final Map<Long, List<ParsedReview>> reviewsBySource =
            new LinkedHashMap<>();

    private final Map<Long, String> collectionStatuses =
            new LinkedHashMap<>();

    private boolean closed;

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.collector = Objects.requireNonNull(collector);

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

        synchronized (this) {
            if (!closed) {
                reviewsBySource.put(
                        source.id(),
                        List.copyOf(reviews)
                );
            }
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
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        executor.shutdownNow();

        collectionStatuses.replaceAll((id, status) ->
                "QUEUED".equals(status) || "RUNNING".equals(status)
                        ? "FAILED"
                        : status
        );
    }
}
