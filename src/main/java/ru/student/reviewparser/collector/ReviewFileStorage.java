package ru.student.reviewparser.collector;

import ru.student.reviewparser.model.ParsedReview;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReviewFileStorage implements ReviewStorage {

    private static final TypeReference<
            Map<Long, List<ParsedReview>>> REVIEW_MAP_TYPE =
            new TypeReference<>() {
            };

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ReviewFileStorage(Path file) {
        this.file = Objects.requireNonNull(file);
    }

    @Override
    public synchronized void save(
            Map<Long, List<ParsedReview>> snapshots
    ) throws IOException {

        Objects.requireNonNull(snapshots);

        Path absoluteFile = file.toAbsolutePath();
        Path directory = absoluteFile.getParent();

        Files.createDirectories(directory);

        Path temporaryFile = Files.createTempFile(
                directory,
                "reviews-",
                ".tmp"
        );

        try {
            String json = mapper.writeValueAsString(snapshots);

            Files.writeString(
                    temporaryFile,
                    json,
                    StandardCharsets.UTF_8
            );

            try {
                Files.move(
                        temporaryFile,
                        absoluteFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporaryFile,
                        absoluteFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    @Override
    public synchronized Map<Long, List<ParsedReview>> load()
            throws IOException {

        if (Files.notExists(file)) {
            return Map.of();
        }

        String json = Files.readString(
                file,
                StandardCharsets.UTF_8
        );

        Map<Long, List<ParsedReview>> loaded =
                mapper.readValue(json, REVIEW_MAP_TYPE);

        if (loaded == null) {
            throw new IOException(
                    "Review storage must contain a JSON object"
            );
        }

        Map<Long, List<ParsedReview>> result =
                new LinkedHashMap<>();

        for (Map.Entry<Long, List<ParsedReview>> entry
                : loaded.entrySet()) {

            result.put(
                    entry.getKey(),
                    List.copyOf(entry.getValue())
            );
        }

        return Collections.unmodifiableMap(result);
    }
}
