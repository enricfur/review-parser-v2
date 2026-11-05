package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.collector.ReviewFileStorage;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceServicePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresReviewsAndKeepsPreviousSnapshotAfterFailure()
            throws Exception {

        Path sourcesFile = tempDir.resolve("sources.json");
        Path reviewsFile = tempDir.resolve("reviews.json");

        String url =
                "https://apps.apple.com/ru/app/test/id123456789";

        ParsedReview review = new ParsedReview(
                "review-1",
                url,
                "Test Author",
                "Test Title",
                "Test review content",
                new BigDecimal("4.5"),
                LocalDate.of(2026, 10, 20)
        );

        AtomicInteger attempts = new AtomicInteger();

        ReviewCollector collector = (sourceKey, sourceUrl) -> {
            if (attempts.incrementAndGet() == 1) {
                return List.of(review);
            }

            throw new IOException("Synthetic collection failure");
        };

        SourceRegistry firstRegistry = new SourceRegistry(
                new SourceFileStorage(sourcesFile)
        );

        SourceService firstService = new SourceService(
                firstRegistry,
                collector,
                0,
                new ReviewFileStorage(reviewsFile)
        );

        long sourceId;

        try {
            SourceRegistry.Source source =
                    firstService.registerAndCollect(url);

            sourceId = source.id();

            assertEquals(
                    List.of(review),
                    firstService.findReviewsBySource(sourceId)
            );

            assertThrows(
                    IOException.class,
                    () -> firstService.registerAndCollect(url)
            );

            assertEquals(
                    "FAILED",
                    firstService.findCollectionStatus(sourceId)
            );

            assertEquals(
                    List.of(review),
                    firstService.findReviewsBySource(sourceId)
            );

        } finally {
            firstService.close();
        }

        // Simulate restarting the application.
        SourceRegistry restoredRegistry = new SourceRegistry(
                new SourceFileStorage(sourcesFile)
        );

        SourceService restoredService = new SourceService(
                restoredRegistry,
                collector,
                0,
                new ReviewFileStorage(reviewsFile)
        );

        try {
            assertEquals(
                    1,
                    restoredRegistry.findAll().size()
            );

            assertEquals(
                    sourceId,
                    restoredRegistry.findAll().get(0).id()
            );

            assertEquals(
                    List.of(review),
                    restoredService.findReviewsBySource(sourceId)
            );

            assertEquals(
                    List.of(review),
                    restoredService.findAllReviews()
            );

        } finally {
            restoredService.close();
        }
    }
}
