package ru.ugk.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.*;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByMessengerAndExternalUserId(MessengerType messenger, String externalUserId);

    void deleteByMessengerAndExternalUserId(MessengerType messenger, String externalUserId);
}
