package ru.ugk.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.repository.*;
import java.util.List;

@Service
@Transactional
public class CatalogService {
    private final EducationLevelRepository levels;
    private final CourseRepository courses;
    private final StudyGroupRepository groups;

    public CatalogService(EducationLevelRepository levels, CourseRepository courses, StudyGroupRepository groups) {
        this.levels = levels; this.courses = courses; this.groups = groups;
    }
    @Transactional(readOnly = true) public List<EducationLevel> activeLevels(){ return levels.findByActiveTrueOrderBySortOrderAscNameAsc(); }
    @Transactional(readOnly = true) public List<Course> activeCourses(Long levelId){ return courses.findByEducationLevelIdAndActiveTrueOrderByNumberAsc(levelId); }
    @Transactional(readOnly = true) public List<StudyGroup> activeGroups(Long courseId){ return groups.findByCourseIdAndActiveTrueOrderByNameAsc(courseId); }
    @Transactional(readOnly = true) public List<EducationLevel> allLevels(){ return levels.findAll(); }
    @Transactional(readOnly = true) public List<Course> allCourses(){ return courses.findAll(); }
    @Transactional(readOnly = true) public List<StudyGroup> allGroups(){ return groups.findAll(); }

    public EducationLevel saveLevel(Long id, String name, Integer maxCourses, Integer sortOrder, boolean active){
        if (maxCourses == null || maxCourses < 1 || maxCourses > 10) throw new IllegalArgumentException("Количество курсов должно быть от 1 до 10");
        EducationLevel e = id == null ? new EducationLevel() : levels.findById(id).orElseThrow();
        e.setName(name.trim()); e.setMaxCourses(maxCourses); e.setSortOrder(sortOrder == null ? 0 : sortOrder); e.setActive(active);
        return levels.save(e);
    }
    public Course saveCourse(Long id, Long levelId, Integer number, String name, boolean active){
        EducationLevel level = levels.findById(levelId).orElseThrow();
        if (number == null || number < 1 || number > level.getMaxCourses())
            throw new IllegalArgumentException("Для «" + level.getName() + "» допустимы курсы 1–" + level.getMaxCourses());
        Course c = id == null ? new Course() : courses.findById(id).orElseThrow();
        c.setEducationLevel(level); c.setNumber(number); c.setName(name == null || name.isBlank() ? number + " курс" : name.trim()); c.setActive(active);
        return courses.save(c);
    }
    public StudyGroup saveGroup(Long id, Long courseId, String name, boolean active){
        Course course = courses.findById(courseId).orElseThrow();
        StudyGroup g = id == null ? new StudyGroup() : groups.findById(id).orElseThrow();
        g.setCourse(course); g.setName(name.trim()); g.setActive(active); return groups.save(g);
    }
    public void deleteLevel(Long id){ levels.deleteById(id); }
    public void deleteCourse(Long id){ courses.deleteById(id); }
    public void deleteGroup(Long id){ groups.deleteById(id); }
}
