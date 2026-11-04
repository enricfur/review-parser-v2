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
    void acceptsDostupnoePravoObjectAndReviewUrls() {
        List<String> valid = List.of(
                "https://dostupnoepravo.ru/review/companies/310-yuridicheskaya-kollegiya",
                "https://dostupnoepravo.ru/review/lawyers/123-ivan-ivanov",
                "https://dostupnoepravo.ru/survey/456-example",
                "https://dostupnoepravo.ru/review/companies/310-yuridicheskaya-kollegiya?page=2"
        );

        for (String url : valid) {
            assertEquals(
                    url,
                    SourceUrlPolicy.validateAndNormalize(url).toString()
            );
        }
    }

    @Test
    void rejectsUnsupportedDostupnoePravoPaths() {
        for (String url : List.of(
                "https://dostupnoepravo.ru/",
                "https://dostupnoepravo.ru/about",
                "https://dostupnoepravo.ru/review/companies/example",
                "https://dostupnoepravo.ru/survey/456-example?page=2",
                "https://dostupnoepravo.ru/review/companies/310-example?page=0",
                "https://dostupnoepravo.ru/review/companies/310-example?other=1"
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SourceUrlPolicy.validateAndNormalize(url),
                    url
            );
        }
    }

    @Test
    void normalizesHostAndDefaultPort() {
        assertEquals(
                "https://apps.apple.com/ru/app/instagram/id389801252",
                SourceUrlPolicy.validateAndNormalize(
                        "https://APPS.APPLE.COM:443/ru/app/instagram/id389801252"
                ).toString()
        );

        assertEquals(
                "https://dostupnoepravo.ru/review/companies/310-example",
                SourceUrlPolicy.validateAndNormalize(
                        "https://DOSTUPNOEPRAVO.RU:443/review/companies/310-example"
                ).toString()
        );
    }

    @Test
    void rejectsUnsupportedPlatforms() {
        for (String url : List.of(
                "https://example.com/reviews",
                "https://reviews.example.org/content/test"
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
                "http://dostupnoepravo.ru/review/companies/310-example",
                "https://example.test/content/test",
                "https://apps.apple.com.example.test/app",
                "https://dostupnoepravo.ru.example.test/review/companies/310-example",
                "https://apps.apple.com:8443/app",
                "https://user@apps.apple.com/app",
                "https://apps.apple.com/app#fragment",
                "https://dostupnoepravo.ru/review/companies/310-example#fragment",
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
