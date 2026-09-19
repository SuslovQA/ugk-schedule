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
    @Bean
    CommandLineRunner createAdmin(AdminUserRepository repo, PasswordEncoder encoder,
                                  @Value("${app.admin.username:admin}") String username,
                                  @Value("${app.admin.password:admin123}") String password) {
        return args -> {
            if (repo.findByUsername(username).isEmpty()) {
                AdminUser u = new AdminUser();
                u.setUsername(username);
                u.setPasswordHash(encoder.encode(password));
                repo.save(u);
                System.out.println("Created initial admin user: " + username + ". Change APP_ADMIN_PASSWORD for production.");
            }
        };
    }
}
