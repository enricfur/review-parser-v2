package ru.student.reviewparser.parser;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleFeedParserTest {

    private static final String SOURCE_URL =
            "https://apps.apple.com/ru/app/instagram/id389801252";

    private static final String FEED_JSON = """
            {
              "feed": {
                "entry": [
                  {
                    "id": {"label": "14724696479"},
                    "author": {
                      "name": {"label": "(Марьям)"}
                    },
                    "title": {"label": "Ну пожалуйста"},
                    "content": {
                      "label": "Сделайте так чтобы инстаграм работал без впн"
                    },
                    "im:rating": {"label": "4"},
                    "updated": {
                      "label": "2026-10-29T00:41:43-07:00"
                    }
                  },
                  {
                    "id": {"label": "14724552720"},
                    "author": {
                      "name": {"label": "EvgeniyaLi"}
                    },
                    "title": {"label": "Кошмар"},
                    "content": {
                      "label": "Ужаснейшее последнее обновление."
                    },
                    "im:rating": {"label": "1"},
                    "updated": {
                      "label": "2026-10-28T23:33:57-07:00"
                    }
                  }
                ]
              }
            }
            """;

    private final AppleFeedParser parser =
            new AppleFeedParser();

    @Test
    void parsesReviewsIntoCommonModel() {
        List<ParsedReview> reviews =
                parser.parse(FEED_JSON, SOURCE_URL);

        assertEquals(2, reviews.size());

        ParsedReview first = reviews.get(0);

        assertEquals("14724696479", first.externalId());
        assertEquals(SOURCE_URL, first.sourceUrl());
        assertEquals("(Марьям)", first.author());
        assertEquals("Ну пожалуйста", first.title());
        assertEquals(
                "Сделайте так чтобы инстаграм работал без впн",
                first.content()
        );
        assertEquals(new BigDecimal("4"), first.rating());
        assertEquals(
                LocalDate.of(2026, 10, 29),
                first.publishedAt()
        );

        ParsedReview second = reviews.get(1);

        assertEquals("14724552720", second.externalId());
        assertEquals("EvgeniyaLi", second.author());
        assertEquals("Кошмар", second.title());
        assertEquals(
                "Ужаснейшее последнее обновление.",
                second.content()
        );
        assertEquals(new BigDecimal("1"), second.rating());
        assertEquals(
                LocalDate.of(2026, 10, 28),
                second.publishedAt()
        );
    }

    @Test
    void skipsMetadataEntryWithoutRating() {
        String json = """
                {
                  "feed": {
                    "entry": [
                      {
                        "id": {"label": "application-metadata"},
                        "title": {"label": "Instagram"}
                      }
                    ]
                  }
                }
                """;

        assertEquals(
                List.of(),
                parser.parse(json, SOURCE_URL)
        );
    }

    @Test
    void returnsEmptyListForEmptyFeed() {
        String json = """
                {
                  "feed": {
                    "entry": []
                  }
                }
                """;

        assertEquals(
                List.of(),
                parser.parse(json, SOURCE_URL)
        );
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("{invalid json", SOURCE_URL)
        );
    }

    @Test
    void rejectsInvalidRatingInReview() {
        String json = FEED_JSON.replace(
                """
                "im:rating": {"label": "4"}
                """.trim(),
                """
                "im:rating": {"label": "invalid"}
                """.trim()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(json, SOURCE_URL)
        );
    }
}
