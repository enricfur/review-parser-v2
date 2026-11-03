package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SourceRegistryTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/instagram/id389801252";

    @Test
    void registersSourceAndAssignsId() {
        SourceRegistry registry = new SourceRegistry();

        var source = registry.register(APP_URL);

        assertEquals(1L, source.id());
        assertEquals(APP_URL, source.url());
    }

    @Test
    void duplicateUrlReturnsExistingSource() {
        SourceRegistry registry = new SourceRegistry();

        var first = registry.register(
                "https://APPS.APPLE.COM:443/ru/app/instagram/id389801252"
        );

        var second = registry.register(APP_URL);

        assertSame(first, second);
        assertEquals(1, registry.findAll().size());
    }

    @Test
    void listsSourcesInRegistrationOrder() {
        SourceRegistry registry = new SourceRegistry();

        var first = registry.register(
                "https://apps.apple.com/ru/app/first/id111111111"
        );

        var second = registry.register(
                "https://apps.apple.com/ru/app/second/id222222222"
        );

        assertEquals(
                List.of(first, second),
                registry.findAll()
        );
    }

    @Test
    void findsSourceById() {
        SourceRegistry registry = new SourceRegistry();

        var source = registry.register(APP_URL);

        assertEquals(source, registry.findById(source.id()));
        assertNull(registry.findById(999));
    }

    @Test
    void rejectsInvalidUrls() {
        SourceRegistry registry = new SourceRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(" ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        "http://apps.apple.com/ru/app/instagram/id389801252"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register("https://")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        "https://user:password@apps.apple.com/ru/app/instagram/id389801252"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        APP_URL + "#section"
                )
        );

        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void concurrentRegistrationDoesNotCreateDuplicates()
            throws Exception {

        SourceRegistry registry = new SourceRegistry();

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(4)) {

            List<Future<SourceRegistry.Source>> futures =
                    new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(
                        () -> registry.register(APP_URL)
                ));
            }

            for (Future<SourceRegistry.Source> future : futures) {
                assertEquals(1L, future.get().id());
            }
        }

        assertEquals(1, registry.findAll().size());
    }

    @Test
    void concurrentRegistrationAssignsUniqueIds()
            throws Exception {

        SourceRegistry registry = new SourceRegistry();

        int sourceCount = 20;
        CountDownLatch start = new CountDownLatch(1);

        List<Future<SourceRegistry.Source>> futures =
                new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(4)) {

            for (int i = 1; i <= sourceCount; i++) {
                final int number = i;

                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to start"
                        );
                    }

                    return registry.register(
                            "https://apps.apple.com/ru/app/test/id"
                                    + (300000000 + number)
                    );
                }));
            }

            start.countDown();

            Set<Long> ids = new HashSet<>();

            for (Future<SourceRegistry.Source> future : futures) {
                SourceRegistry.Source source =
                        future.get(5, TimeUnit.SECONDS);

                assertTrue(
                        ids.add(source.id()),
                        "Duplicate source ID: " + source.id()
                );

                assertEquals(
                        source,
                        registry.findById(source.id())
                );
            }

            assertEquals(sourceCount, ids.size());
            assertEquals(sourceCount, registry.findAll().size());

            for (long id = 1; id <= sourceCount; id++) {
                assertTrue(
                        ids.contains(id),
                        "Missing source ID: " + id
                );
            }
        }
    }
}
