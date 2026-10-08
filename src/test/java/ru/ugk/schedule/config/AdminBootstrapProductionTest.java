package ru.ugk.schedule.config;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.ugk.schedule.domain.AdminUser;
import ru.ugk.schedule.repository.AdminUserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminBootstrapProductionTest {
    @Test void productionKeepsExistingTestCredentialsByDefault() throws Exception {
        var repo=mock(AdminUserRepository.class);var encoder=new BCryptPasswordEncoder();
        var admin=new AdminUser();admin.setUsername("admin");admin.setPasswordHash(encoder.encode("admin123"));
        when(repo.findByUsername("admin")).thenReturn(Optional.of(admin));var env=new MockEnvironment();env.setActiveProfiles("prod");
        new AdminBootstrapConfig().createAdmin(repo,encoder,"admin","admin123",env).run();
        verify(repo,never()).findAll();verify(repo,never()).save(any());
    }
    @Test void productionRejectsExistingDefaultPasswordWithoutOverwritingIt() {
        var repo=mock(AdminUserRepository.class);var encoder=new BCryptPasswordEncoder();
        var admin=new AdminUser();admin.setUsername("old");admin.setPasswordHash(encoder.encode("admin123"));
        when(repo.findAll()).thenReturn(List.of(admin));var env=new MockEnvironment()
            .withProperty("app.production.enforce-secret-policy","true");env.setActiveProfiles("prod");
        var runner=new AdminBootstrapConfig().createAdmin(repo,encoder,"new","a3X9m7Q2j6V4e8R1",env);
        assertThatThrownBy(() -> runner.run()).isInstanceOf(IllegalStateException.class);
        verify(repo,never()).save(any());
    }
    @Test void strongExistingPasswordIsPreserved() throws Exception {
        var repo=mock(AdminUserRepository.class);var encoder=new BCryptPasswordEncoder();
        var admin=new AdminUser();admin.setUsername("operator");admin.setPasswordHash(encoder.encode("a3X9m7Q2j6V4e8R1"));
        when(repo.findAll()).thenReturn(List.of(admin));when(repo.findByUsername("operator")).thenReturn(Optional.of(admin));
        var env=new MockEnvironment();env.setActiveProfiles("prod");
        new AdminBootstrapConfig().createAdmin(repo,encoder,"operator","z7nAx4Jq9E2yK6mR",env).run();
        verify(repo,never()).save(any());
        assertThat(encoder.matches("a3X9m7Q2j6V4e8R1",admin.getPasswordHash())).isTrue();
    }
}
