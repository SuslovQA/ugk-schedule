package ru.ugk.schedule.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.production.enforce-secret-policy", havingValue = "true")
    static BeanFactoryPostProcessor validateProductionConfiguration(Environment environment) {
        // Validate before datasource/Flyway initialization; never include secret values.
        return factory -> validate(environment);
    }
    static void validate(Environment env) {
        requirePassword(env, "spring.datasource.password", false);
        requirePassword(env, "app.admin.password", true);
        String user=env.getProperty("spring.datasource.username", "");
        if (user.isBlank() || user.equalsIgnoreCase("postgres"))
            throw new IllegalStateException("Production requires a dedicated DB_USERNAME, not postgres");
        if (env.getProperty("app.admin.username", "").isBlank())
            throw new IllegalStateException("Production requires APP_ADMIN_USERNAME");
        if (env.getProperty("app.logging.history-days", Integer.class, 0) <= 0)
            throw new IllegalStateException("Production requires positive APP_LOG_HISTORY_DAYS");
        if (!env.getProperty("app.logging.total-size-cap", "").matches("(?i)[1-9][0-9]*(KB|MB|GB)"))
            throw new IllegalStateException("Production requires positive APP_LOG_TOTAL_SIZE_CAP");
        if (!env.getProperty("app.telegram.token", "").isBlank() || !env.getProperty("app.max.token", "").isBlank()) {
            try {
                URI uri=URI.create(env.getProperty("app.miniapp.url", ""));
                String host=uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !host.contains(".")
                        || host.equalsIgnoreCase("example.com") || host.toLowerCase(Locale.ROOT).endsWith(".example.com")
                        || host.equals("127.0.0.1") || host.endsWith(".localhost")
                        || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Production bot requires a public HTTPS MINIAPP_URL");
            }
        }
    }
    private static void requirePassword(Environment env, String property, boolean bcrypt) {
        String value=env.getProperty(property, "");
        String normalized=value.toLowerCase(Locale.ROOT);
        if (value.length() < 16 || value.isBlank() || normalized.contains("replace_with")
                || normalized.contains("your_unique") || normalized.contains("password")
                || normalized.equals("admin123") || normalized.equals("postgres")
                || (bcrypt && value.getBytes(StandardCharsets.UTF_8).length > 72))
            throw new IllegalStateException("Invalid production secret: " + property + "; use a unique password of at least 16 characters");
    }
}
