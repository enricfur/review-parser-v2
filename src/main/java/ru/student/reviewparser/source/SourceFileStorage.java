package ru.student.reviewparser.source;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public class SourceFileStorage implements SourceStorage {

    private static final TypeReference<List<SourceRegistry.Source>>
            SOURCE_LIST_TYPE = new TypeReference<>() {
    };

    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public SourceFileStorage(Path file) {
        this.file = Objects.requireNonNull(file);
    }

    @Override
    public synchronized void save(List<SourceRegistry.Source> sources)
            throws IOException {

        Objects.requireNonNull(sources);

        Path absoluteFile = file.toAbsolutePath();
        Path directory = absoluteFile.getParent();

        Files.createDirectories(directory);

        Path temporaryFile = Files.createTempFile(
                directory,
                "sources-",
                ".tmp"
        );

        try {
            String json = mapper.writeValueAsString(sources);

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
    public synchronized List<SourceRegistry.Source> load()
            throws IOException {

        if (Files.notExists(file)) {
            return List.of();
        }

        String json = Files.readString(
                file,
                StandardCharsets.UTF_8
        );

        List<SourceRegistry.Source> loaded =
                mapper.readValue(json, SOURCE_LIST_TYPE);

        if (loaded == null) {
            throw new IOException(
                    "Source storage must contain a JSON array"
            );
        }

        return List.copyOf(loaded);
    }
}
