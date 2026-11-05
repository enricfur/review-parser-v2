package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SourceRegistryPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresSourcesAndContinuesIdsAfterRestart()
            throws Exception {

        Path file = tempDir.resolve("sources.json");

        SourceRegistry firstRegistry = new SourceRegistry(
                new SourceFileStorage(file)
        );

        String firstUrl =
                "https://apps.apple.com/ru/app/test/id123456789";

        String secondUrl =
                "https://apps.apple.com/ru/app/test/id234567890";

        String thirdUrl =
                "https://apps.apple.com/ru/app/test/id345678901";

        SourceRegistry.Source first =
                firstRegistry.register(firstUrl);

        SourceRegistry.Source second =
                firstRegistry.register(secondUrl);

        assertEquals(1, first.id());
        assertEquals(2, second.id());

        // Simulate application restart by creating a new registry.
        SourceRegistry restoredRegistry = new SourceRegistry(
                new SourceFileStorage(file)
        );

        assertEquals(
                List.of(first, second),
                restoredRegistry.findAll()
        );

        SourceRegistry.Source existing =
                restoredRegistry.register(firstUrl);

        assertEquals(first.id(), existing.id());
        assertEquals(2, restoredRegistry.findAll().size());

        SourceRegistry.Source third =
                restoredRegistry.register(thirdUrl);

        assertEquals(3, third.id());
        assertEquals(3, restoredRegistry.findAll().size());

        SourceRegistry.Source found =
                restoredRegistry.findById(third.id());

        assertNotNull(found);
        assertEquals(thirdUrl, found.url());

        // Verify that the new source was also written to JSON.
        SourceRegistry finalRegistry = new SourceRegistry(
                new SourceFileStorage(file)
        );

        assertEquals(
                List.of(first, second, third),
                finalRegistry.findAll()
        );
    }
}
