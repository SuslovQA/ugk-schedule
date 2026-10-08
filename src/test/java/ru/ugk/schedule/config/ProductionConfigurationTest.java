package ru.ugk.schedule.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class ProductionConfigurationTest {
    private MockEnvironment good() {
        return new MockEnvironment().withProperty("spring.datasource.username","ugk_schedule")
            .withProperty("spring.datasource.password","z7nAx4Jq9E2yK6mR")
            .withProperty("app.admin.username","operator").withProperty("app.admin.password","a3X9m7Q2j6V4e8R1")
            .withProperty("app.logging.history-days","30").withProperty("app.logging.total-size-cap","1GB");
    }
    @Test void acceptsConfiguredSecrets() { assertThatCode(() -> ProductionConfiguration.validate(good())).doesNotThrowAnyException(); }
    @Test void rejectsDefaultsAndNeverEchoesSecrets() {
        for(String property : java.util.List.of("spring.datasource.password","app.admin.password")) {
            MockEnvironment env=good().withProperty(property,"REPLACE_WITH_STRONG_ADMIN_PASSWORD");
            assertThatThrownBy(() -> ProductionConfiguration.validate(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property).hasMessageNotContaining("REPLACE_WITH_STRONG_ADMIN_PASSWORD");
        }
        assertThatThrownBy(() -> ProductionConfiguration.validate(good().withProperty("spring.datasource.username","postgres")))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void validatesRetentionAndBotHttpsUrl() {
        assertThatThrownBy(() -> ProductionConfiguration.validate(good().withProperty("app.logging.history-days","0")))
            .isInstanceOf(IllegalStateException.class);
        var env=good().withProperty("app.telegram.token","test-token").withProperty("app.miniapp.url","http://example.com");
        assertThatThrownBy(() -> ProductionConfiguration.validate(env)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> ProductionConfiguration.validate(env.withProperty("app.miniapp.url","https://schedule.test/miniapp/schedule")))
            .doesNotThrowAnyException();
    }
}
