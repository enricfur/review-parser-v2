package ru.student.reviewparser.collector;

import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.source.SourceUrlPolicy;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

public final class RoutingReviewCollector implements ReviewCollector {

    private final ReviewCollector appleCollector;
    private final ReviewCollector dostupnoePravoCollector;

    public RoutingReviewCollector(
            ReviewCollector appleCollector,
            ReviewCollector dostupnoePravoCollector
    ) {
        this.appleCollector =
                Objects.requireNonNull(appleCollector);
        this.dostupnoePravoCollector =
                Objects.requireNonNull(dostupnoePravoCollector);
    }

    @Override
    public List<ParsedReview> collectFromObject(
            String sourceKey,
            String objectUrl
    ) throws IOException, InterruptedException {

        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Source key must not be blank"
            );
        }

        URI uri = SourceUrlPolicy.validateAndNormalize(
                objectUrl
        );

        return switch (uri.getHost()) {
            case "apps.apple.com" ->
                    appleCollector.collectFromObject(
                            sourceKey,
                            uri.toASCIIString()
                    );

            case "dostupnoepravo.ru" ->
                    dostupnoePravoCollector.collectFromObject(
                            sourceKey,
                            uri.toASCIIString()
                    );

            default -> throw new IllegalArgumentException(
                    "Unsupported review source host"
            );
        };
    }
}
