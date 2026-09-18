package ru.ugk.schedule.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ugk.schedule.domain.Course;
import java.util.List;
public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByEducationLevelIdAndNumber(Long educationLevelId, Integer number);
    List<Course> findByEducationLevelIdAndActiveTrueOrderByNumberAsc(Long educationLevelId);
}
