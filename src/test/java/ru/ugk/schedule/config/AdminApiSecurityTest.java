package ru.ugk.schedule.config;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import ru.ugk.schedule.controller.api.AdminScheduleApiController;
import ru.ugk.schedule.repository.AdminUserRepository;
import ru.ugk.schedule.service.ScheduleService;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AdminScheduleApiController.class)
@Import(SecurityConfig.class)
class AdminApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminUserRepository admins;
    @MockitoBean ScheduleService schedules;
    @Test void writesRequireCsrfEvenForAdmin() throws Exception {
        mvc.perform(delete("/api/admin/schedule/7").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/schedule").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/schedule/7").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        verifyNoInteractions(schedules);
    }
    @Test void validCsrfAllowsAdminButNotStudent() throws Exception {
        mvc.perform(delete("/api/admin/schedule/7").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
        verify(schedules).delete(7L);
        mvc.perform(delete("/api/admin/schedule/8").with(user("student").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
        verify(schedules,never()).delete(8L);
    }
    @Test void unknownEndpointsDenyAccess() throws Exception {
        mvc.perform(get("/actuator/env").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
}
