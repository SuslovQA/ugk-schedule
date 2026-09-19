package ru.ugk.schedule.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.repository.*;

import java.util.Optional;

@Service
@Transactional
public class UserPreferenceService {
    private final UserPreferenceRepository prefs;
    private final EducationLevelRepository levels;
    private final CourseRepository courses;
    private final StudyGroupRepository groups;

    public UserPreferenceService(UserPreferenceRepository prefs, EducationLevelRepository levels, CourseRepository courses, StudyGroupRepository groups) {
        this.prefs = prefs;
        this.levels = levels;
        this.courses = courses;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public Optional<UserPreference> find(MessengerType m, String userId) {
        return prefs.findByMessengerAndExternalUserId(m, userId);
    }

    public UserPreference ensure(MessengerType m, String userId) {
        return prefs.findByMessengerAndExternalUserId(m, userId).orElseGet(() -> {
            UserPreference p = new UserPreference();
            p.setMessenger(m);
            p.setExternalUserId(userId);
            return prefs.save(p);
        });
    }

    public void setLevel(MessengerType m, String userId, Long id) {
        UserPreference p = ensure(m, userId);
        p.setEducationLevel(levels.findById(id).orElseThrow());
        p.setCourse(null);
        p.setGroup(null);
    }

    public void setCourse(MessengerType m, String userId, Long id) {
        UserPreference p = ensure(m, userId);
        Course c = courses.findById(id).orElseThrow();
        if (p.getEducationLevel() == null || !c.getEducationLevel().getId().equals(p.getEducationLevel().getId()))
            throw new IllegalArgumentException("Курс не относится к выбранному уровню");
        p.setCourse(c);
        p.setGroup(null);
    }

    public void setGroup(MessengerType m, String userId, Long id) {
        UserPreference p = ensure(m, userId);
        StudyGroup g = groups.findById(id).orElseThrow();
        if (p.getCourse() == null || !g.getCourse().getId().equals(p.getCourse().getId()))
            throw new IllegalArgumentException("Группа не относится к выбранному курсу");
        p.setGroup(g);
    }

    public void reset(MessengerType m, String userId) {
        prefs.deleteByMessengerAndExternalUserId(m, userId);
    }
}
