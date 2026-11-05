package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresSources() throws Exception {
        Path file = tempDir.resolve("data").resolve("sources.json");

        SourceFileStorage storage = new SourceFileStorage(file);

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

        SourceFileStorage restoredStorage =
                new SourceFileStorage(file);

        assertEquals(
                sources,
                restoredStorage.load()
        );
    }
}
