package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SourceControllerTest {

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
}
