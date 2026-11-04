package ru.student.reviewparser.source;

import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SourceService {

    private final SourceRegistry registry;
    private final ReviewCollector collector;

    private final Map<Long, List<ParsedReview>> reviewsBySource =
            new LinkedHashMap<>();

    private final Map<Long, String> collectionStatuses =
            new LinkedHashMap<>();

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.collector = Objects.requireNonNull(collector);
    }

    public SourceRegistry.Source registerAndCollect(
            String url
    ) throws IOException, InterruptedException {

        SourceRegistry.Source source = registry.register(url);
        collectAndStore(source);

        synchronized (this) {
            collectionStatuses.put(source.id(), "COMPLETED");
        }

        return source;
    }

    public SourceRegistry.Source registerAndCollectAsync(
            String url
    ) {
        SourceRegistry.Source source = registry.register(url);

        synchronized (this) {
            if ("RUNNING".equals(collectionStatuses.get(source.id()))) {
                return source;
            }

            collectionStatuses.put(source.id(), "RUNNING");
        }

        Runnable task = () -> {
            try {
                collectAndStore(source);

                synchronized (this) {
                    collectionStatuses.put(source.id(), "COMPLETED");
                }
            } catch (IOException exception) {
                markFailed(source.id());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markFailed(source.id());
            } catch (RuntimeException exception) {
                markFailed(source.id());
            }
        };

        Thread thread = new Thread(
                task,
                "review-collector-" + source.id()
        );

        thread.start();

        return source;
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
            reviewsBySource.put(
                    source.id(),
                    List.copyOf(reviews)
            );
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
}
