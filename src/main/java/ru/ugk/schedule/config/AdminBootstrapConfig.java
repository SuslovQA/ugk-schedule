package ru.ugk.schedule.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ugk.schedule.domain.AdminUser;
import ru.ugk.schedule.repository.AdminUserRepository;

@Configuration
public class AdminBootstrapConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminBootstrapConfig.class);
    @Bean
    CommandLineRunner createAdmin(AdminUserRepository repo, PasswordEncoder encoder,
                                  @Value("${app.admin.username:admin}") String username,
                                  @Value("${app.admin.password:admin123}") String password,
                                  org.springframework.core.env.Environment environment) {
        return args -> {
            if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))
                    && environment.getProperty("app.production.enforce-secret-policy", Boolean.class, false)) {
                for (AdminUser admin : repo.findAll()) {
                    if (admin.isEnabled() && java.util.List.of("admin123", "admin", "password", "postgres")
                            .stream().anyMatch(weak -> encoder.matches(weak, admin.getPasswordHash())))
                        throw new IllegalStateException("Disable or rotate existing default admin password before production startup");
                }
            }
            if (repo.findByUsername(username).isEmpty()) {
                AdminUser u = new AdminUser();
                u.setUsername(username);
                u.setPasswordHash(encoder.encode(password));
                repo.save(u);
                log.info("Created initial admin user: " + username + ". Change APP_ADMIN_PASSWORD for production.");
            }
        };
    }
}
