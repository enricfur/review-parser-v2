package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceUrlPolicyTest {

    @Test
    void acceptsAllowedAppleHosts() {
        assertEquals(
                "apps.apple.com",
                SourceUrlPolicy.validateAndNormalize(
                        "https://apps.apple.com/ru/app/instagram/id389801252"
                ).getHost()
        );

        assertEquals(
                "itunes.apple.com",
                SourceUrlPolicy.validateAndNormalize(
                        "https://itunes.apple.com/ru/rss/customerreviews/id=389801252/sortby=mostrecent/json"
                ).getHost()
        );
    }

    @Test
    void normalizesHostAndDefaultPort() {
        assertEquals(
                "https://apps.apple.com/ru/app/instagram/id389801252",
                SourceUrlPolicy.validateAndNormalize(
                        "https://APPS.APPLE.COM:443/ru/app/instagram/id389801252"
                ).toString()
        );
    }

    @Test
    void rejectsUnsupportedPlatforms() {
        for (String url : List.of(
                "https://irecommend.ru/content/example",
                "https://www.irecommend.ru/content/example",
                "https://dostupnoepravo.ru/"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SourceUrlPolicy.validateAndNormalize(url),
                    url
            );
        }
    }

    @Test
    void normalizationIsIdempotentForCyrillicPath() {
        String once = SourceUrlPolicy.validateAndNormalize(
                "https://apps.apple.com/ru/app/инстаграм/id389801252"
        ).toASCIIString();

        String twice = SourceUrlPolicy.validateAndNormalize(once)
                .toASCIIString();

        assertEquals(once, twice);
    }

    @Test
    void rejectsUnsafeUrls() {
        List<String> invalid = List.of(
                "",
                "not a URL",
                "http://apps.apple.com/ru/app/instagram/id389801252",
                "https://example.test/content/test",
                "https://apps.apple.com.example.test/app",
                "https://apps.apple.com:8443/app",
                "https://user@apps.apple.com/app",
                "https://apps.apple.com/app#fragment",
                "https://127.0.0.1/content/test",
                "https://localhost/content/test"
        );

        for (String url : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SourceUrlPolicy.validateAndNormalize(url),
                    url
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> SourceUrlPolicy.validateAndNormalize(null)
        );
    }
}
