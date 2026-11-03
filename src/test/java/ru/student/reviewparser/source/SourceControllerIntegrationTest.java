package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.student.reviewparser.collector.ReviewCollector;
import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SourceControllerIntegrationTest {

    private static final String APP_URL =
            "https://apps.apple.com/ru/app/integration-test/id123456789";

    private static final String FAILED_URL =
            "https://apps.apple.com/ru/app/failing-test/id987654321";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postSourceCollectsReviewsAndExposesThemThroughAnswer()
            throws Exception {

        String response = mockMvc.perform(post("/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://apps.apple.com/ru/app/integration-test/id123456789"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.url").value(APP_URL))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long sourceId = extractSourceId(response);

        mockMvc.perform(get("/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.url == '" + APP_URL + "')]"
                ).isNotEmpty());

        mockMvc.perform(get("/answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.author == 'Integration Author')]"
                ).isNotEmpty());

        mockMvc.perform(get("/answer")
                        .param("sourceId", Long.toString(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId")
                        .value("integration-review-1"))
                .andExpect(jsonPath("$[0].author")
                        .value("Integration Author"))
                .andExpect(jsonPath("$[0].rating")
                        .value(5));
    }

    @Test
    void collectionFailureReturnsBadGateway() throws Exception {
        mockMvc.perform(post("/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://apps.apple.com/ru/app/failing-test/id987654321"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error")
                        .value("SOURCE_LOADING_FAILED"));
    }

    private static long extractSourceId(String response) {
        String prefix = "\"id\":";
        int start = response.indexOf(prefix);

        if (start < 0) {
            throw new AssertionError(
                    "Source ID is missing: " + response
            );
        }

        start += prefix.length();
        int end = start;

        while (end < response.length()
                && Character.isDigit(response.charAt(end))) {
            end++;
        }

        return Long.parseLong(
                response.substring(start, end)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineCollectorConfiguration {

        @Bean
        @Primary
        ReviewCollector offlineReviewCollector() {
            return (sourceKey, url) -> {
                if (FAILED_URL.equals(url)) {
                    throw new IOException(
                            "Simulated Apple loading failure"
                    );
                }

                if (!APP_URL.equals(url)) {
                    throw new IOException(
                            "Unexpected test URL: " + url
                    );
                }

                return List.of(
                        new ParsedReview(
                                "integration-review-1",
                                url,
                                "Integration Author",
                                "Integration Title",
                                "Integration review content",
                                new BigDecimal("5"),
                                LocalDate.of(2026, 11, 3)
                        )
                );
            };
        }
    }
}
