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

    public SourceService(
            SourceRegistry registry,
            ReviewCollector collector
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.collector = Objects.requireNonNull(collector);
    }

    public synchronized SourceRegistry.Source registerAndCollect(
            String url
    ) throws IOException, InterruptedException {

        SourceRegistry.Source source = registry.register(url);

        List<ParsedReview> reviews =
                collector.collectFromObject(
                        Long.toString(source.id()),
                        source.url()
                );

        reviewsBySource.put(
                source.id(),
                List.copyOf(reviews)
        );

        return source;
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
