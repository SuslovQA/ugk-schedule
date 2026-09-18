package ru.ugk.schedule.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.repository.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogBulkCreationTest {
    private final EducationLevelRepository levels = mock(EducationLevelRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final CatalogService service = new CatalogService(levels, courses, groups);
    private EducationLevel bachelor, master;

    @BeforeEach void setup() {
        bachelor = level(1L, "Бакалавриат", 4);
        master = level(2L, "Магистратура", 2);
        when(levels.findById(1L)).thenReturn(Optional.of(bachelor));
        when(levels.findById(2L)).thenReturn(Optional.of(master));
    }

    @Test void createsIndependentGroupsAcrossCoursesAndLevels() {
        Course first = course(10L, bachelor), second = course(20L, master);
        when(courses.findById(10L)).thenReturn(Optional.of(first));
        when(courses.findById(20L)).thenReturn(Optional.of(second));
        var result = service.createGroups(List.of(10L, 20L, 10L), " ДХО ", true);
        assertThat(result).isEqualTo(new CatalogService.CreationResult(2, 0));
        var saved = ArgumentCaptor.forClass(StudyGroup.class);
        verify(groups, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(StudyGroup::getCourse).containsExactly(first, second);
        assertThat(saved.getAllValues()).extracting(StudyGroup::getName).containsOnly("ДХО");
        assertThat(saved.getAllValues().get(0)).isNotSameAs(saved.getAllValues().get(1));
    }

    @Test void existingGroupIsNotOverwritten() {
        when(courses.findById(10L)).thenReturn(Optional.of(course(10L, bachelor)));
        when(groups.existsByCourseIdAndName(10L, "ДХО")).thenReturn(true);
        assertThat(service.createGroups(List.of(10L), "ДХО", false))
                .isEqualTo(new CatalogService.CreationResult(0, 1));
        verify(groups, never()).save(any());
    }

    @Test void invalidCourseDoesNotPartiallyCreateGroups() {
        when(courses.findById(10L)).thenReturn(Optional.of(course(10L, bachelor)));
        assertThatThrownBy(() -> service.createGroups(List.of(10L, 999L), "ДХО", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(groups, never()).save(any());
    }

    @Test void createsCourseInSeveralLevelsAndSkipsExisting() {
        when(courses.existsByEducationLevelIdAndNumber(1L, 2)).thenReturn(true);
        assertThat(service.createCourses(List.of(1L, 2L, 2L), 2, "", true))
                .isEqualTo(new CatalogService.CreationResult(1, 1));
        var saved = ArgumentCaptor.forClass(Course.class);
        verify(courses).save(saved.capture());
        assertThat(saved.getValue().getEducationLevel()).isSameAs(master);
        assertThat(saved.getValue().getName()).isEqualTo("2 курс");
    }

    @Test void checksAllCourseLimitsBeforeSaving() {
        assertThatThrownBy(() -> service.createCourses(List.of(1L, 2L), 3, "", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Магистратура");
        verify(courses, never()).save(any());
    }

    @Test void rejectsMissingSelectionsAndBlankGroupName() {
        assertThatThrownBy(() -> service.createGroups(null, "ДХО", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createGroups(List.of(10L), " ", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createCourses(List.of(), 1, "", true)).isInstanceOf(IllegalArgumentException.class);
        verify(groups, never()).save(any());
        verify(courses, never()).save(any());
    }

    private EducationLevel level(Long id, String name, int max) {
        var level = new EducationLevel(); level.setId(id); level.setName(name); level.setMaxCourses(max); return level;
    }
    private Course course(Long id, EducationLevel level) {
        var course = new Course(); course.setId(id); course.setEducationLevel(level); course.setNumber(1); course.setName("1 курс"); return course;
    }
}
