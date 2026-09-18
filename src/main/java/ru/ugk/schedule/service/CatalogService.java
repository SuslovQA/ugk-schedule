package ru.ugk.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.repository.*;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;

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
    public record CreationResult(int created, int skipped) {}

    public CreationResult createCourses(List<Long> levelIds, Integer number, String name, boolean active) {
        List<EducationLevel> selected = new ArrayList<>();
        for (Long levelId : selectedIds(levelIds, "Выберите хотя бы один уровень образования")) {
            EducationLevel level = levels.findById(levelId)
                    .orElseThrow(() -> new IllegalArgumentException("Уровень образования не найден"));
            if (number == null || number < 1 || number > level.getMaxCourses())
                throw new IllegalArgumentException("Для «" + level.getName() + "» допустимы курсы 1–" + level.getMaxCourses());
            selected.add(level);
        }
        String courseName = checkedName(name == null || name.isBlank() ? number + " курс" : name, 120);
        int created = 0;
        for (EducationLevel level : selected) {
            if (courses.existsByEducationLevelIdAndNumber(level.getId(), number)) continue;
            Course course = new Course();
            course.setEducationLevel(level); course.setNumber(number); course.setName(courseName); course.setActive(active);
            courses.save(course); created++;
        }
        return new CreationResult(created, selected.size() - created);
    }

    public CreationResult createGroups(List<Long> courseIds, String name, boolean active) {
        String groupName = checkedName(name, 180);
        List<Course> selected = new ArrayList<>();
        for (Long courseId : selectedIds(courseIds, "Выберите хотя бы один курс")) {
            selected.add(courses.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("Курс не найден")));
        }
        int created = 0;
        for (Course course : selected) {
            if (groups.existsByCourseIdAndName(course.getId(), groupName)) continue;
            StudyGroup group = new StudyGroup();
            group.setCourse(course); group.setName(groupName); group.setActive(active);
            groups.save(group); created++;
        }
        return new CreationResult(created, selected.size() - created);
    }

    private LinkedHashSet<Long> selectedIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id < 1))
            throw new IllegalArgumentException(message);
        return new LinkedHashSet<>(ids);
    }

    private String checkedName(String name, int maxLength) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Введите название");
        String trimmed = name.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("Название не должно превышать " + maxLength + " символов");
        return trimmed;
    }
    public void deleteLevel(Long id){ levels.deleteById(id); }
    public void deleteCourse(Long id){ courses.deleteById(id); }
    public void deleteGroup(Long id){ groups.deleteById(id); }
}
