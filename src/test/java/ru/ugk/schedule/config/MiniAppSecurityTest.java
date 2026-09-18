package ru.ugk.schedule.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.ugk.schedule.controller.MiniAppController;
import ru.ugk.schedule.repository.AdminUserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MiniAppController.class)
@Import(SecurityConfig.class)
class MiniAppSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminUserRepository admins;

    @Test
    void miniAppCanBeEmbeddedByMessengers() throws Exception {
        mvc.perform(get("/miniapp/schedule"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().string("Content-Security-Policy",
                        "frame-ancestors 'self' https://max.ru https://*.max.ru https://web.telegram.org https://*.telegram.org"));
    }

    @Test
    void adminStillRequiresLoginAndCannotBeEmbedded() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
