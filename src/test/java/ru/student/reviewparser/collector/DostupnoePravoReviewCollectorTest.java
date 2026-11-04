package ru.student.reviewparser.collector;

import org.junit.jupiter.api.Test;
import ru.student.reviewparser.model.ParsedReview;
import ru.student.reviewparser.parser.DostupnoePravoReviewParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DostupnoePravoReviewCollectorTest {

    private static final String OBJECT_URL =
            "https://dostupnoepravo.ru/review/companies/310-yuridicheskaya-kollegiya";

    private static final String REVIEW_URL =
            "https://dostupnoepravo.ru/survey/97150-yuridicheskaya-kollegiya";

    @Test
    void collectsLinkedAndInlineReviews() throws Exception {
        String page = """
                <div class="review-single" data-id="97150">
                  <a class="review-text-dark"
                     href="/survey/97150-yuridicheskaya-kollegiya">
                    Читать отзыв
                  </a>
                </div>
                %s
                """.formatted(card(97149));

        List<String> loadedUrls = new ArrayList<>();

        HtmlLoader loader = url -> {
            loadedUrls.add(url);

            if (OBJECT_URL.equals(url)) {
                return page;
            }

            if (REVIEW_URL.equals(url)) {
                return reviewHtml();
            }

            throw new IOException("Unexpected URL: " + url);
        };

        DostupnoePravoReviewCollector collector =
                new DostupnoePravoReviewCollector(
                        loader,
                        new DostupnoePravoReviewParser()
                );

        List<ParsedReview> reviews =
                collector.collectFromObject("1", OBJECT_URL);

        assertEquals(2, reviews.size());
        assertEquals(
                List.of(OBJECT_URL, REVIEW_URL),
                loadedUrls
        );

        assertEquals(REVIEW_URL, reviews.get(0).sourceUrl());
        assertEquals("Annastey", reviews.get(0).author());

        assertEquals(
                OBJECT_URL + "#review-97149",
                reviews.get(1).sourceUrl()
        );
        assertEquals("Тестовый автор", reviews.get(1).author());
    }

    @Test
    void collectsThreePagesWithoutDuplicateReviews()
            throws Exception {

        String page2 = OBJECT_URL + "?page=2";
        String page3 = OBJECT_URL + "?page=3";

        List<String> loadedUrls = new ArrayList<>();

        HtmlLoader loader = url -> {
            loadedUrls.add(url);

            if (OBJECT_URL.equals(url)) {
                return card(100)
                        + card(101)
                        + nextPage(2);
            }

            if (page2.equals(url)) {
                return card(101)
                        + card(102)
                        + nextPage(3);
            }

            if (page3.equals(url)) {
                return card(103)
                        + card(104)
                        + nextPage(4);
            }

            throw new IOException("Unexpected URL: " + url);
        };

        DostupnoePravoReviewCollector collector =
                new DostupnoePravoReviewCollector(
                        loader,
                        new DostupnoePravoReviewParser()
                );

        List<ParsedReview> reviews =
                collector.collectFromObject("1", OBJECT_URL);

        assertEquals(
                List.of(OBJECT_URL, page2, page3),
                loadedUrls
        );

        assertEquals(5, reviews.size());
    }

    private String card(int id) {
        return """
                <div class="review-single" data-id="%d">
                  <span class="review-rating-stars-number">5</span>
                  <div class="review-text">Хорошая работа.</div>
                  <span class="review-object-name">
                    О коллегии адвокатов: Юридическая Коллегия
                  </span>
                  <div class="client-name">
                    Автор отзыва: Тестовый автор
                  </div>
                  <div class="review-date-city">
                    <span>21.04.2026</span>
                  </div>
                </div>
                """.formatted(id);
    }

    private String nextPage(int page) {
        return """
                <ul class="pagination">
                  <li class="page-item next">
                    <a class="page-link"
                       href="%s?page=%d">Далее</a>
                  </li>
                </ul>
                """.formatted(
                "/review/companies/310-yuridicheskaya-kollegiya",
                page
        );
    }

    private String reviewHtml() {
        return """
                <div id="single-review-page-v2">
                  <div class="single-review-v3">
                    <span class="review-rating-stars-number">5</span>
                    <h1 class="title-reviews">Консультация</h1>
                    <div class="review-text">Спасибо за помощь.</div>
                    <div class="client-name">
                      Автор отзыва: Annastey | Краснодар
                    </div>
                    <div class="review-date-city">
                      <span>21.04.2026</span>
                    </div>
                  </div>
                </div>
                """;
    }
}
