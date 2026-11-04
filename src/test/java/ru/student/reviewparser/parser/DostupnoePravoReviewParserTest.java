package ru.student.reviewparser.parser;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DostupnoePravoReviewParserTest {

    private static final String REVIEW_URL =
            "https://dostupnoepravo.ru/survey/97150-yuridicheskaya-kollegiya";

    private static final String OBJECT_URL =
            "https://dostupnoepravo.ru/review/companies/310-yuridicheskaya-kollegiya";

    private final DostupnoePravoReviewParser parser =
            new DostupnoePravoReviewParser();

    @Test
    void parsesReviewWithRequiredFields() {
        ParsedReview review = parser.parse(validHtml(), REVIEW_URL);

        assertEquals("Annastey", review.author());
        assertEquals("Консультация", review.title());
        assertEquals("Первый абзац. Второй абзац.", review.content());
        assertEquals(new BigDecimal("5"), review.rating());
        assertEquals(LocalDate.of(2026, 4, 21), review.publishedAt());
    }

    @Test
    void parsesCardWithoutIndividualReviewLink() {
        ParsedReview review = parser.parseCard(
                validCardHtml(),
                OBJECT_URL
        );

        assertEquals("Городской", review.author());
        assertEquals("Юридическая Коллегия", review.title());
        assertEquals(
                "С Андреем работалось отлично. Рекомендую!",
                review.content()
        );
        assertEquals(new BigDecimal("5"), review.rating());
        assertEquals(LocalDate.of(2026, 4, 21), review.publishedAt());
    }

    @Test
    void rejectsMissingReviewText() {
        String html = validHtml().replace(
                "<div class=\"review-text\">Первый абзац. Второй абзац.</div>",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(html, REVIEW_URL)
        );
    }

    private String validHtml() {
        return """
                <div id="single-review-page-v2">
                  <div class="single-review-v3">
                    <span class="review-rating-stars-number">5</span>
                    <h1 class="title-reviews">Консультация</h1>
                    <div class="review-text">Первый абзац. Второй абзац.</div>
                    <div class="client-name">
                      <span>Автор отзыва: </span>Annastey | Краснодар
                    </div>
                    <div class="review-date-city">
                      <span>21.04.2026</span>
                    </div>
                  </div>
                </div>
                """;
    }

    private String validCardHtml() {
        return """
                <div class="review-single" data-id="97149">
                  <span class="review-rating-stars-number">5</span>
                  <div class="review-text">
                    С Андреем работалось отлично. Рекомендую!
                  </div>
                  <span class="review-object-name">
                    О коллегии адвокатов: Юридическая Коллегия
                  </span>
                  <div class="client-name">
                    Автор отзыва: Городской | Краснодар
                  </div>
                  <div class="review-date-city">
                    <span>21.04.2026</span>
                  </div>
                </div>
                """;
    }
}
