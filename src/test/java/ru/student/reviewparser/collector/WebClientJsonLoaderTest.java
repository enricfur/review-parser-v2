package ru.student.reviewparser.collector;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientJsonLoaderTest {

    private static final String URL =
            "https://itunes.apple.com/ru/rss/customerreviews/id=389801252/sortby=mostrecent/json";

    @Test
    void returnsJsonFromSuccessfulResponse() throws Exception {
        String json = """
                {"feed":{"entry":[]}}
                """;

        var loader = loaderReturning(
                HttpStatus.OK,
                json
        );

        assertEquals(json, loader.load(URL));
    }

    @Test
    void sendsJsonAcceptHeader() throws Exception {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> {
                    assertTrue(
                            request.headers()
                                    .getAccept()
                                    .contains(MediaType.APPLICATION_JSON)
                    );

                    return Mono.just(
                            ClientResponse.create(HttpStatus.OK)
                                    .body("{\"feed\":{\"entry\":[]}}")
                                    .build()
                    );
                })
                .build();

        var loader = new WebClientJsonLoader(client);

        loader.load(URL);
    }

    @Test
    void rejectsUnsafeUrlsWithoutSendingRequest() {
        AtomicInteger requests = new AtomicInteger();

        WebClient client = WebClient.builder()
                .exchangeFunction(request -> {
                    requests.incrementAndGet();

                    return Mono.just(
                            ClientResponse.create(HttpStatus.OK)
                                    .body("{}")
                                    .build()
                    );
                })
                .build();

        var loader = new WebClientJsonLoader(client);

        List<String> invalidUrls = List.of(
                "",
                "not a URL",
                "http://itunes.apple.com/ru/rss/customerreviews/id=389801252/sortby=mostrecent/json",
                "https://example.test/feed",
                "https://itunes.apple.com.example.test/feed",
                "https://itunes.apple.com:8443/feed",
                "https://user@itunes.apple.com/feed",
                "https://itunes.apple.com/feed#fragment"
        );

        for (String invalidUrl : invalidUrls) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> loader.load(invalidUrl),
                    invalidUrl
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(null)
        );

        assertEquals(0, requests.get());
    }

    @Test
    void rejectsForbiddenResponse() {
        var loader = loaderReturning(
                HttpStatus.FORBIDDEN,
                "Access denied"
        );

        assertThrows(
                IOException.class,
                () -> loader.load(URL)
        );
    }

    @Test
    void rejectsRedirectResponse() {
        var loader = loaderReturning(
                HttpStatus.FOUND,
                "Redirect"
        );

        assertThrows(
                IOException.class,
                () -> loader.load(URL)
        );
    }

    @Test
    void rejectsEmptyResponse() {
        var loader = loaderReturning(
                HttpStatus.OK,
                ""
        );

        assertThrows(
                IOException.class,
                () -> loader.load(URL)
        );
    }

    private static WebClientJsonLoader loaderReturning(
            HttpStatus status,
            String body
    ) {
        WebClient client = WebClient.builder()
                .exchangeFunction(request ->
                        Mono.just(
                                ClientResponse.create(status)
                                        .header(
                                                HttpHeaders.CONTENT_TYPE,
                                                "application/json; charset=UTF-8"
                                        )
                                        .body(body)
                                        .build()
                        )
                )
                .build();

        return new WebClientJsonLoader(client);
    }
}
