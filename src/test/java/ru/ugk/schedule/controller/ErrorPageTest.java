package ru.ugk.schedule.controller;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.ugk.schedule.config.SecurityConfig;
import ru.ugk.schedule.repository.AdminUserRepository;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MiniAppController.class)
@ImportAutoConfiguration(ErrorMvcAutoConfiguration.class)
@Import(SecurityConfig.class)
class ErrorPageTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminUserRepository admins;

    @ParameterizedTest
    @CsvSource({"400,Не удалось выполнить запрос", "403,Нет доступа", "404,Страница не найдена", "500,Не удалось открыть страницу"})
    void rendersFriendlyHtmlWithoutInternalDetails(int code, String title) throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "private-database-details")
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/missing"))
                .andExpect(status().is(code)).andExpect(view().name("error"))
                .andExpect(content().string(containsString(title)))
                .andExpect(content().string(not(containsString("private-database-details"))));
    }

    @Test void keepsJsonResponsesForApiErrors() throws Exception {
        mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "private-database-details"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void miniAppErrorRemainsEmbeddable() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().string("Content-Security-Policy", containsString("https://max.ru")));
    }
}
