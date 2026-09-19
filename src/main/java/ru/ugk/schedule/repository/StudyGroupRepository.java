package ru.ugk.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.StudyGroup;

import java.util.List;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
    boolean existsByCourseIdAndName(Long courseId, String name);

    List<StudyGroup> findByCourseIdAndActiveTrueOrderByNameAsc(Long courseId);
}
