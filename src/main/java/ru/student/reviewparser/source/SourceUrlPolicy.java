package ru.student.reviewparser.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public final class SourceUrlPolicy {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of(
                    "apps.apple.com",
                    "itunes.apple.com"
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
}
