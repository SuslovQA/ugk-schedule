package ru.ugk.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.ScheduleEntry;

import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findByGroupIdOrderByDayOfWeekAscStartTimeAsc(Long groupId);
}
