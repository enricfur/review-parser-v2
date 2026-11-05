package ru.student.reviewparser.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresReviews() throws Exception {
        Path file = tempDir.resolve("data").resolve("reviews.json");

        ReviewFileStorage storage = new ReviewFileStorage(file);

        assertTrue(storage.load().isEmpty());

        ParsedReview firstReview = new ParsedReview(
                "review-1",
                "https://dostupnoepravo.ru/",
                "Первый автор",
                "Первый отзыв",
                "Текст первого отзыва",
                new BigDecimal("4.5"),
                LocalDate.of(2026, 10, 20)
        );

        ParsedReview secondReview = new ParsedReview(
                "review-2",
                "https://apps.apple.com/ru/app/test/id123456789",
                "Второй автор",
                "Второй отзыв",
                "Текст второго отзыва",
                new BigDecimal("5.0"),
                LocalDate.of(2026, 10, 21)
        );

        Map<Long, List<ParsedReview>> snapshots =
                new LinkedHashMap<>();

        snapshots.put(1L, List.of(firstReview));
        snapshots.put(2L, List.of(secondReview));

        storage.save(snapshots);

        ReviewFileStorage restoredStorage =
                new ReviewFileStorage(file);

        assertEquals(
                snapshots,
                restoredStorage.load()
        );
    }
}
