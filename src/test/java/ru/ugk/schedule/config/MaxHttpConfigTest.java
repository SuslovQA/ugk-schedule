package ru.ugk.schedule.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import static org.assertj.core.api.Assertions.*;

class MaxHttpConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class))
            .withUserConfiguration(MaxHttpConfig.class)
            .withPropertyValues("spring.ssl.bundle.pem.max.truststore.certificate=classpath:certs/russian_trusted_root_ca.cer");

    @Test
    void loadsBundledCertificateAndCreatesClient() {
        context.run(app -> assertThat(app).hasNotFailed().hasBean("maxRestClient"));
    }

    // Opt-in live TLS check: no bot token and no messages or polling.
    @Test
    @EnabledIfSystemProperty(named = "max.tls.smoke", matches = "true")
    void reachesMaxOverVerifiedTls() {
        context.run(app -> assertThatThrownBy(() -> app.getBean("maxRestClient", RestClient.class)
                .get().uri("https://platform-api2.max.ru/me").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class));
    }
}
