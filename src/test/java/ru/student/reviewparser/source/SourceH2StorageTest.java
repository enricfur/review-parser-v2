package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceH2StorageTest {

    @Test
    void savesAndRestoresSourcesWithoutDuplicates() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema.sql")
                .build();

        try {
            SourceH2Storage storage = new SourceH2Storage(
                    new JdbcTemplate(database),
                    new TransactionTemplate(
                            new DataSourceTransactionManager(database)
                    )
            );

            assertTrue(storage.load().isEmpty());

            List<SourceRegistry.Source> sources = List.of(
                    new SourceRegistry.Source(
                            1,
                            "https://apps.apple.com/ru/app/test/id123456789"
                    ),
                    new SourceRegistry.Source(
                            2,
                            "https://dostupnoepravo.ru/"
                    )
            );

            storage.save(sources);
            assertEquals(sources, storage.load());

            storage.save(sources);
            assertEquals(sources, storage.load());

        } finally {
            database.shutdown();
        }
    }
}
