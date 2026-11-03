package ru.student.reviewparser.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SourceRegistry {

    private final Map<String, Source> sources =
            new LinkedHashMap<>();

    private long nextId = 1;

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
