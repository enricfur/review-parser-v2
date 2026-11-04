package ru.student.reviewparser.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public final class SourceUrlPolicy {

    private static final String DOSTUPNOE_PRAVO_HOST =
            "dostupnoepravo.ru";

    private static final Set<String> ALLOWED_HOSTS =
            Set.of(
                    "apps.apple.com",
                    "itunes.apple.com",
                    DOSTUPNOE_PRAVO_HOST
            );

    private SourceUrlPolicy() {
    }

    public static URI validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Source URL must not be blank"
            );
        }

        try {
            URI uri = new URI(rawUrl.trim());

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (!"https".equalsIgnoreCase(scheme)
                    || host == null
                    || !ALLOWED_HOSTS.contains(
                    host.toLowerCase(Locale.ROOT))
                    || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1
                    && uri.getPort() != 443)
                    || uri.getRawFragment() != null
                    || uri.isOpaque()) {

                throw new IllegalArgumentException(
                        "Only HTTPS URLs on supported "
                                + "review platforms are allowed"
                );
            }

            String normalizedHost =
                    host.toLowerCase(Locale.ROOT);

            String path = uri.getRawPath() == null
                    ? ""
                    : uri.getRawPath();

            String query = uri.getRawQuery() == null
                    ? ""
                    : "?" + uri.getRawQuery();

            if (DOSTUPNOE_PRAVO_HOST.equals(normalizedHost)
                    && !isSupportedDostupnoePravoPath(
                    path,
                    uri.getRawQuery())) {

                throw new IllegalArgumentException(
                        "Unsupported Dostupnoe Pravo URL"
                );
            }

            return new URI(
                    "https://" + normalizedHost + path + query
            ).normalize();

        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid source URL",
                    exception
            );
        }
    }

    private static boolean isSupportedDostupnoePravoPath(
            String path,
            String query
    ) {
        boolean objectPage = path.matches(
                "/review/(?:companies|lawyers)/"
                        + "[0-9]+-[a-zA-Z0-9-]+/?"
        );

        boolean reviewPage = path.matches(
                "/survey/[0-9]+-[a-zA-Z0-9-]+/?"
        );

        if (reviewPage) {
            return query == null;
        }

        if (objectPage) {
            return query == null
                    || query.matches("page=[1-9][0-9]*");
        }

        return false;
    }
}
