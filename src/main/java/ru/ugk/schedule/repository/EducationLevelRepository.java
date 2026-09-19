package ru.ugk.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.EducationLevel;

import java.util.List;

public interface EducationLevelRepository extends JpaRepository<EducationLevel, Long> {
    List<EducationLevel> findByActiveTrueOrderBySortOrderAscNameAsc();
}
