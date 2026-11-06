package ru.student.reviewparser.collector;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReviewH2Storage implements ReviewStorage {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ReviewH2Storage(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate);
    }

    @Override
    public Map<Long, List<ParsedReview>> load()
            throws IOException {

        try {
            Map<Long, List<ParsedReview>> restored =
                    new LinkedHashMap<>();

            jdbcTemplate.query(
                    "SELECT id FROM sources ORDER BY id",
                    rs -> {
                        restored.put(
                                rs.getLong("id"),
                                new ArrayList<>()
                        );
                    }
            );

            jdbcTemplate.query(
                    """
                    SELECT source_id, external_id, source_url,
                           author, title, content, rating,
                           published_at
                    FROM reviews
                    ORDER BY source_id, review_position, external_id
                    """,
                    rs -> {
                        long sourceId = rs.getLong("source_id");

                        Date publishedDate =
                                rs.getDate("published_at");

                        BigDecimal rating =
                                rs.getBigDecimal("rating");

                        if (rating != null) {
                            rating = rating.stripTrailingZeros();
                        }

                        ParsedReview review = new ParsedReview(
                                rs.getString("external_id"),
                                rs.getString("source_url"),
                                rs.getString("author"),
                                rs.getString("title"),
                                rs.getString("content"),
                                rating,
                                publishedDate == null
                                        ? null
                                        : publishedDate.toLocalDate()
                        );

                        restored.computeIfAbsent(
                                sourceId,
                                ignored -> new ArrayList<>()
                        ).add(review);
                    }
            );

            Map<Long, List<ParsedReview>> result =
                    new LinkedHashMap<>();

            for (Map.Entry<Long, List<ParsedReview>> entry
                    : restored.entrySet()) {

                result.put(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                );
            }

            return java.util.Collections.unmodifiableMap(result);

        } catch (DataAccessException exception) {
            throw new IOException(
                    "Could not load reviews from H2",
                    exception
            );
        }
    }

    @Override
    public void save(
            Map<Long, List<ParsedReview>> snapshots
    ) throws IOException {

        Objects.requireNonNull(snapshots);

        Map<Long, List<ParsedReview>> sourceReviews =
                new LinkedHashMap<>();

        for (Map.Entry<Long, List<ParsedReview>> entry
                : snapshots.entrySet()) {

            Long sourceId = entry.getKey();

            if (sourceId == null || sourceId <= 0) {
                throw new IOException(
                        "Invalid source ID: " + sourceId
                );
            }

            sourceReviews.put(
                    sourceId,
                    List.copyOf(entry.getValue())
            );
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {

                for (Map.Entry<Long, List<ParsedReview>> entry
                        : sourceReviews.entrySet()) {

                    long sourceId = entry.getKey();

                    jdbcTemplate.update(
                            "DELETE FROM reviews WHERE source_id = ?",
                            sourceId
                    );

                    List<ParsedReview> reviews = entry.getValue();

                    for (int position = 0;
                         position < reviews.size();
                         position++) {

                        ParsedReview review = reviews.get(position);

                        jdbcTemplate.update(
                                """
                                INSERT INTO reviews (
                                    source_id,
                                    external_id,
                                    source_url,
                                    author,
                                    title,
                                    content,
                                    rating,
                                    published_at,
                                    review_position
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                                sourceId,
                                review.externalId(),
                                review.sourceUrl(),
                                review.author(),
                                review.title(),
                                review.content(),
                                review.rating(),
                                review.publishedAt(),
                                position
                        );
                    }
                }
            });

        } catch (DataAccessException exception) {
            throw new IOException(
                    "Could not save reviews to H2",
                    exception
            );
        }
    }
}
