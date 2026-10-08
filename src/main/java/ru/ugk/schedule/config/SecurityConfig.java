package ru.ugk.schedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.ugk.schedule.repository.AdminUserRepository;

@Configuration
public class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain miniAppSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/miniapp/**", "/error")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .headers(h -> h.frameOptions(f -> f.disable())
                        .contentSecurityPolicy(c -> c.policyDirectives(
                                "default-src 'self'; script-src 'self' https://st.max.ru; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: https://*.max.ru; connect-src 'self' https://*.max.ru; "
                                + "object-src 'none'; base-uri 'none'; form-action 'self'; "
                                + "frame-ancestors 'self' https://max.ru https://*.max.ru https://web.telegram.org https://*.telegram.org")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AdminUserRepository repo) {
        return username -> repo.findByUsername(username)
                .map(u -> org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                        .password(u.getPasswordHash()).roles("ADMIN").disabled(!u.isEnabled()).build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/css/**", "/js/**", "/miniapp/**", "/api/public/**", "/error").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .formLogin(f -> f.loginPage("/admin/login").defaultSuccessUrl("/admin/schedule", true).permitAll())
                .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
                .csrf(c -> c.ignoringRequestMatchers("/api/public/telegram/group", "/api/public/max/group"));
        return http.build();
    }
}
