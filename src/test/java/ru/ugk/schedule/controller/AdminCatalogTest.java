package ru.ugk.schedule.controller;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.ugk.schedule.config.SecurityConfig;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.repository.AdminUserRepository;
import ru.ugk.schedule.service.CatalogService;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles="ADMIN")
class AdminCatalogTest {
    @Autowired MockMvc mvc;
    @MockitoBean CatalogService catalog;
    @MockitoBean AdminUserRepository admins;

    @Test void rendersCheckboxesForCoursesAndEducationLevels() throws Exception {
        var level = new EducationLevel(); level.setId(1L); level.setName("Бакалавриат"); level.setMaxCourses(4);
        var course = new Course(); course.setId(10L); course.setEducationLevel(level); course.setName("1 курс"); course.setNumber(1);
        var group = new StudyGroup(); group.setId(5L); group.setName("ДХО"); group.setCourse(course);
        when(catalog.allLevels()).thenReturn(List.of(level));
        when(catalog.allCourses()).thenReturn(List.of(course));
        when(catalog.allGroups()).thenReturn(List.of(group));
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"levelIds\"")))
                .andExpect(content().string(containsString("name=\"courseIds\"")))
                .andExpect(content().string(containsString("ДХО")));
    }

    @Test void postsMultipleCourseIds() throws Exception {
        when(catalog.createGroups(List.of(10L,20L), "ДХО", true))
                .thenReturn(new CatalogService.CreationResult(2,0));
        mvc.perform(post("/admin/groups/bulk").with(csrf()).param("courseIds","10","20")
                        .param("name","ДХО").param("active","true"))
                .andExpect(redirectedUrl("/admin")).andExpect(flash().attributeExists("message"));
        verify(catalog).createGroups(List.of(10L,20L), "ДХО", true);
    }

    @Test void postsMultipleLevelIds() throws Exception {
        when(catalog.createCourses(List.of(1L,2L), 2, "", true))
                .thenReturn(new CatalogService.CreationResult(2,0));
        mvc.perform(post("/admin/courses/bulk").with(csrf()).param("levelIds","1","2")
                        .param("number","2").param("active","true"))
                .andExpect(redirectedUrl("/admin")).andExpect(flash().attributeExists("message"));
        verify(catalog).createCourses(List.of(1L,2L), 2, "", true);
    }
}
