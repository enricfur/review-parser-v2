package ru.student.reviewparser.parser;

import ru.student.reviewparser.model.ParsedReview;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AppleFeedParser {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    public List<ParsedReview> parse(
            String json,
            String sourceUrl
    ) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(
                    "Apple feed JSON must not be blank"
            );
        }

        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceUrl must not be blank"
            );
        }

        final JsonNode root;

        try {
            root = mapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Invalid Apple feed JSON",
                    exception
            );
        }

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Apple feed must be a JSON object"
            );
        }

        JsonNode entries = root.path("feed").path("entry");

        if (!entries.isArray()) {
            throw new IllegalArgumentException(
                    "Apple feed.entry must be an array"
            );
        }

        List<ParsedReview> reviews = new ArrayList<>();

        for (JsonNode entry : entries) {

            // Apple may include an application metadata
            // entry without a review rating.
            if (entry.path("im:rating").isMissingNode()) {
                continue;
            }

            String externalId =
                    requiredLabel(entry, "id");

            String author = requiredLabel(
                    entry.path("author"),
                    "name"
            );

            String title =
                    requiredLabel(entry, "title");

            String content =
                    requiredLabel(entry, "content");

            String ratingText =
                    requiredLabel(entry, "im:rating");

            String updated =
                    requiredLabel(entry, "updated");

            final BigDecimal rating;

            try {
                rating = new BigDecimal(ratingText);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid Apple review rating",
                        exception
                );
            }

            final java.time.LocalDate publishedAt;

            try {
                publishedAt = OffsetDateTime.parse(updated)
                        .toLocalDate();
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Invalid Apple review date",
                        exception
                );
            }

            reviews.add(new ParsedReview(
                    externalId,
                    sourceUrl,
                    author,
                    title,
                    content,
                    rating,
                    publishedAt
            ));
        }

        return List.copyOf(reviews);
    }

    private String requiredLabel(
            JsonNode parent,
            String field
    ) {
        JsonNode label = parent.path(field).path("label");

        if (!label.isTextual()
                || label.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid Apple review field: "
                            + field
            );
        }

        return label.asText();
    }
}
