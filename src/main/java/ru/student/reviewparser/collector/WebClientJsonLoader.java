package ru.student.reviewparser.collector;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import ru.student.reviewparser.source.SourceUrlPolicy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class WebClientJsonLoader implements JsonLoader {

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(20);

    private static final int MAX_RESPONSE_BYTES =
            2 * 1024 * 1024;

    private final WebClient client;

    public WebClientJsonLoader() {
        this(createClient());
    }

    // Allows offline tests with a mocked HTTP exchange.
    WebClientJsonLoader(WebClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String load(String sourceUrl)
            throws IOException, InterruptedException {

        URI uri = SourceUrlPolicy
                .validateAndNormalize(sourceUrl);

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException(
                    "JSON loading interrupted before request"
            );
        }

        try {
            String json = client.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(
                            status -> status.value() != 200,
                            response -> response.createException()
                    )
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);

            if (json == null || json.isBlank()) {
                throw new IOException(
                        "Source returned an empty JSON response"
                );
            }

            return json;

        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                InterruptedException interrupted =
                        new InterruptedException(
                                "JSON loading interrupted"
                        );
                interrupted.initCause(exception);
                throw interrupted;
            }

            throw new IOException(
                    "Failed to load JSON from " + uri,
                    exception
            );
        }
    }

    private static WebClient createClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        return WebClient.builder()
                .clientConnector(
                        new JdkClientHttpConnector(httpClient)
                )
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "review-parser-v2/0.0.1"
                )
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(MAX_RESPONSE_BYTES)
                )
                .build();
    }
}
