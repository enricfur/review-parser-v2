package ru.student.reviewparser.source;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SourceH2Storage implements SourceStorage {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public SourceH2Storage(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate);
    }

    @Override
    public List<SourceRegistry.Source> load() throws IOException {
        try {
            return jdbcTemplate.query(
                    "SELECT id, url FROM sources ORDER BY id",
                    (rs, rowNum) -> new SourceRegistry.Source(
                            rs.getLong("id"),
                            rs.getString("url")
                    )
            );
        } catch (DataAccessException exception) {
            throw new IOException(
                    "Could not load registered sources from H2",
                    exception
            );
        }
    }

    @Override
    public void save(List<SourceRegistry.Source> sources)
            throws IOException {

        Objects.requireNonNull(sources);

        try {
            transactionTemplate.executeWithoutResult(status -> {

                Map<Long, String> existingById = new HashMap<>();
                Set<String> existingUrls = new HashSet<>();

                jdbcTemplate.query(
                        "SELECT id, url FROM sources",
                        rs -> {
                            existingById.put(
                                    rs.getLong("id"),
                                    rs.getString("url")
                            );
                            existingUrls.add(rs.getString("url"));
                        }
                );

                for (SourceRegistry.Source source : sources) {
                    String existingUrl =
                            existingById.get(source.id());

                    if (existingUrl != null) {
                        if (!existingUrl.equals(source.url())) {
                            throw new IllegalStateException(
                                    "Source ID already belongs "
                                            + "to another URL"
                            );
                        }
                        continue;
                    }

                    if (existingUrls.contains(source.url())) {
                        throw new IllegalStateException(
                                "Source URL already belongs "
                                        + "to another ID"
                        );
                    }

                    jdbcTemplate.update(
                            "INSERT INTO sources (id, url) VALUES (?, ?)",
                            source.id(),
                            source.url()
                    );

                    existingById.put(source.id(), source.url());
                    existingUrls.add(source.url());
                }
            });
        } catch (DataAccessException | IllegalStateException exception) {
            throw new IOException(
                    "Could not save registered sources to H2",
                    exception
            );
        }
    }
}
