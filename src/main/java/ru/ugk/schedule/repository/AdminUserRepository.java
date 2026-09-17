package ru.ugk.schedule.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.AdminUser;
import java.util.Optional;
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
}
