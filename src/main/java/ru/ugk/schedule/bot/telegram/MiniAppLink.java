package ru.ugk.schedule.bot.telegram;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

final class MiniAppLink {
    private MiniAppLink() {}

    static Optional<String> forGroup(String value, Long groupId) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) return Optional.empty();
            host = host.toLowerCase(Locale.ROOT);
            if (!host.contains(".") || host.equals("example.com") || host.endsWith(".example.com")
                    || host.equals("127.0.0.1") || host.endsWith(".localhost")) return Optional.empty();
            return Optional.of(UriComponentsBuilder.fromUri(uri)
                    .replaceQueryParam("groupId", groupId).build(true).toUriString());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
