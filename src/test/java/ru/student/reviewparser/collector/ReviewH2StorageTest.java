package ru.student.reviewparser.collector;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.source.SourceH2Storage;
import ru.student.reviewparser.source.SourceRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewH2StorageTest {

    @Test
    void savesAndRestoresReviewsInOrder() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema.sql")
                .build();

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);

            TransactionTemplate transactionTemplate =
                    new TransactionTemplate(
                            new DataSourceTransactionManager(database)
                    );

            SourceH2Storage sourceStorage = new SourceH2Storage(
                    jdbcTemplate,
                    transactionTemplate
            );

            ReviewH2Storage reviewStorage = new ReviewH2Storage(
                    jdbcTemplate,
                    transactionTemplate
            );

            sourceStorage.save(List.of(
                    new SourceRegistry.Source(
                            1,
                            "https://dostupnoepravo.ru/"
                    ),
                    new SourceRegistry.Source(
                            2,
                            "https://apps.apple.com/ru/app/test/id123456789"
                    )
            ));

            assertTrue(reviewStorage.load().get(1L).isEmpty());

            ParsedReview firstReview = new ParsedReview(
                    "review-1",
                    "https://dostupnoepravo.ru/review/1",
                    "Первый автор",
                    "Первый отзыв",
                    "Текст первого отзыва",
                    new BigDecimal("5"),
                    LocalDate.of(2026, 10, 20)
            );

            ParsedReview secondReview = new ParsedReview(
                    "review-2",
                    "https://dostupnoepravo.ru/review/2",
                    "Второй автор",
                    "Второй отзыв",
                    "Текст второго отзыва",
                    new BigDecimal("4"),
                    LocalDate.of(2026, 10, 21)
            );

            Map<Long, List<ParsedReview>> snapshots =
                    new LinkedHashMap<>();

            snapshots.put(
                    1L,
                    List.of(firstReview, secondReview)
            );

            snapshots.put(2L, List.of());

            reviewStorage.save(snapshots);

            assertEquals(
                    snapshots,
                    reviewStorage.load()
            );

        } finally {
            database.shutdown();
        }
    }
}
