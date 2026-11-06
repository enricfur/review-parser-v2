package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SourceControllerTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/instagram/id389801252";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getSourcesReturnsJsonArray() throws Exception {
        mockMvc.perform(get("/sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAnswerReturnsJsonArray() throws Exception {
        mockMvc.perform(get("/answer"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAnswerUsesUtf8Encoding() throws Exception {
        mockMvc.perform(get("/answer"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding(
                        StandardCharsets.UTF_8.name()
                ));
    }

    @Test
    void getAnswerRejectsUnknownSourceId() throws Exception {
        mockMvc.perform(get("/answer")
                        .param("sourceId", "999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("INVALID_INPUT"));
    }

    @Test
    void invalidSourceUrlReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("INVALID_INPUT"));
    }

    @Test
    void invalidJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("INVALID_JSON"));
    }

    @Test
    void filtersAndSortsReviewsUsingParallelStream() throws Exception {
        ParsedReview lowRated = review("low", "2");
        ParsedReview mediumRated = review("medium", "4");
        ParsedReview highRated = review("high", "5");

        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) -> List.of(
                        lowRated,
                        mediumRated,
                        highRated
                )
        );

        try {
            SourceRegistry.Source source =
                    service.registerAndCollect(APP_URL);

            SourceController controller =
                    new SourceController(service);

            List<ParsedReview> filtered = controller.getAnswer(
                    source.id(),
                    new BigDecimal("4"),
                    "rating",
                    "desc"
            );

            assertEquals(
                    List.of(highRated, mediumRated),
                    filtered
            );

            assertEquals(
                    List.of(lowRated, mediumRated, highRated),
                    controller.getAnswer(null, null, null, "asc")
            );
        } finally {
            service.close();
        }
    }

    @Test
    void rejectsUnsupportedSortBy() {
        SourceService service = new SourceService(
                new SourceRegistry(),
                (sourceKey, url) -> List.of()
        );

        try {
            SourceController controller =
                    new SourceController(service);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> controller.getAnswer(
                            null,
                            null,
                            "unknown",
                            "asc"
                    )
            );
        } finally {
            service.close();
        }
    }

    private static ParsedReview review(
            String id,
            String rating
    ) {
        return new ParsedReview(
                id,
                APP_URL,
                "Test author",
                "Test title",
                "Test content",
                new BigDecimal(rating),
                LocalDate.of(2026, 11, 6)
        );
    }
}
