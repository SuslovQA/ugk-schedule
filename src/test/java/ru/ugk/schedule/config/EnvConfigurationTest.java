package ru.ugk.schedule.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class EnvConfigurationTest {
    @TempDir Path directory;

    @Test
    void importsDotEnvAndResolvesApplicationSettings() throws Exception {
        Path env = directory.resolve(".env");
        Files.writeString(env, """
                MINIAPP_URL=https://schedule.test/miniapp/schedule
                MAX_POLL_DELAY_MS=3500
                MAX_CA_CERTIFICATE=classpath:certs/russian_trusted_root_ca.cer
                TELEGRAM_BOT_TOKEN=test-token
                """);
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:application.properties",
                        "spring.config.import=optional:" + env.toUri() + "[.properties]")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var settings = context.getEnvironment();
                    assertThat(settings.getProperty("app.miniapp.url"))
                            .isEqualTo("https://schedule.test/miniapp/schedule");
                    assertThat(settings.getProperty("app.max.poll-delay-ms")).isEqualTo("3500");
                    assertThat(settings.getProperty("app.telegram.token")).isEqualTo("test-token");
                    assertThat(settings.getProperty("spring.ssl.bundle.pem.max.truststore.certificate"))
                            .isEqualTo("classpath:certs/russian_trusted_root_ca.cer");
                });
    }
}
