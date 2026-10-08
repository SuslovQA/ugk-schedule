package ru.ugk.schedule.controller;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.ugk.schedule.controller.api.PublicApiController;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PublicScheduleVisibilityTest {
    @Test void inactiveGroupOrParentDoesNotPublishSchedule() {
        var catalog=mock(CatalogService.class);var schedules=mock(ScheduleService.class);
        var api=new PublicApiController(catalog,schedules);
        var level=new EducationLevel();var course=new Course();course.setEducationLevel(level);
        var group=new StudyGroup();group.setId(7L);group.setName("Test");group.setCourse(course);
        when(catalog.findGroup(7L)).thenReturn(Optional.of(group));
        group.setActive(false);
        assertThatThrownBy(() -> api.schedule(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        group.setActive(true);course.setActive(false);
        assertThatThrownBy(() -> api.group(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        course.setActive(true);level.setActive(false);
        assertThatThrownBy(() -> api.schedule(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(schedules);
        level.setActive(true);when(schedules.getByGroup(7L)).thenReturn(List.of());
        assertThat(api.schedule(7L)).isEqualTo(List.of());
    }
}
