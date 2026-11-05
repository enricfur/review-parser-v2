package ru.student.reviewparser.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SourceRegistry {

    private final Map<String, Source> sources =
            new LinkedHashMap<>();

    private final SourceStorage storage;

    private long nextId = 1;

    public SourceRegistry() {
        this.storage = null;
    }

    public SourceRegistry(SourceStorage storage)
            throws IOException {

        this.storage = Objects.requireNonNull(storage);

        List<Source> restored = storage.load();
        Set<Long> restoredIds = new HashSet<>();

        for (Source source : restored) {
            if (source == null
                    || source.id() <= 0
                    || source.id() == Long.MAX_VALUE
                    || !restoredIds.add(source.id())) {

                throw new IOException(
                        "Source storage contains an invalid "
                                + "or duplicate source ID"
                );
            }

            String normalizedUrl;

            try {
                normalizedUrl = SourceUrlPolicy
                        .validateAndNormalize(source.url())
                        .toASCIIString();
            } catch (RuntimeException exception) {
                throw new IOException(
                        "Source storage contains an invalid URL",
                        exception
                );
            }

            if (!normalizedUrl.equals(source.url())
                    || sources.containsKey(normalizedUrl)) {

                throw new IOException(
                        "Source storage contains duplicate "
                                + "or non-normalized URLs"
                );
            }

            sources.put(normalizedUrl, source);

            if (source.id() >= nextId) {
                nextId = source.id() + 1;
            }
        }
    }

    public synchronized Source register(String rawUrl) {
        String url = SourceUrlPolicy
                .validateAndNormalize(rawUrl)
                .toASCIIString();

        Source existing = sources.get(url);

        if (existing != null) {
            return existing;
        }

        if (nextId == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "Source ID limit reached"
            );
        }

        Source source = new Source(nextId, url);

        if (storage != null) {
            List<Source> updated =
                    new ArrayList<>(sources.values());

            updated.add(source);

            try {
                storage.save(updated);
            } catch (IOException exception) {
                throw new UncheckedIOException(
                        "Could not save registered sources",
                        exception
                );
            }
        }

        sources.put(url, source);
        nextId++;

        return source;
    }

    public synchronized List<Source> findAll() {
        return List.copyOf(
                new ArrayList<>(sources.values())
        );
    }

    public synchronized Source findById(long id) {
        return sources.values().stream()
                .filter(source -> source.id() == id)
                .findFirst()
                .orElse(null);
    }

    public record Source(long id, String url) {
    }
}
