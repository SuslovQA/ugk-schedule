package ru.ugk.schedule.bot.max;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MaxBotServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void clearsSetupBeforeMenuEvenIfOneDeletionFails(boolean deletionFails) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var prefs = mock(UserPreferenceService.class);
        var level = new EducationLevel(); level.setName("СПО");
        var course = new Course(); course.setName("1 курс");
        var group = new StudyGroup(); group.setId(4L); group.setName("Группа");
        var preference = new UserPreference();
        preference.setEducationLevel(level); preference.setCourse(course); preference.setGroup(group);
        when(prefs.find(MessengerType.MAX, "7")).thenReturn(Optional.empty(), Optional.of(preference));
        server.expect(requestTo("https://platform-api2.max.ru/updates?timeout=1&limit=100"))
                .andRespond(withSuccess("""
                        {"updates":[
                        {"update_type":"bot_started","user":{"user_id":7}},
                        {"update_type":"message_callback","callback":{"user":{"user_id":7},"payload":"L:2"},"message":{"body":{"mid":"m1"}}},
                        {"update_type":"message_callback","callback":{"user":{"user_id":7},"payload":"C:3"},"message":{"body":{"mid":"m2"}}},
                        {"update_type":"message_callback","callback":{"user":{"user_id":7},"payload":"G:4"},"message":{"body":{"mid":"m3"}}}]}
                        """, MediaType.APPLICATION_JSON));
        for (int id = 1; id <= 3; id++) {
            server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=7"))
                    .andRespond(withSuccess("{\"message\":{\"body\":{\"mid\":\"m" + id + "\"}}}", MediaType.APPLICATION_JSON));
        }
        for (int id = 1; id <= 3; id++) {
            server.expect(requestTo("https://platform-api2.max.ru/messages?message_id=m" + id))
                    .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                    .andExpect(header("Authorization", "test-token"))
                    .andRespond(deletionFails && id == 1 ? withBadRequest() : withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://platform-api2.max.ru/me"))
                .andRespond(withSuccess("{\"user_id\":123,\"is_bot\":true,\"username\":\"schedule_bot\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=7"))
                .andExpect(jsonPath("$.text").value("Настройки сохранены: СПО, 1 курс, Группа"))
                .andExpect(jsonPath("$.attachments[0].payload.buttons.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].text").value("Показать расписание"))
                .andExpect(jsonPath("$.attachments[0].payload.buttons[1][0].payload").value("RESET"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new MaxBotService(mock(CatalogService.class), prefs, "test-token", builder.build()).poll();
        verify(prefs).setGroup(MessengerType.MAX, "7", 4L);
        server.verify();
    }

    @Test
    void opensAppByAuthenticatedBotIdAndCachesIdentity() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var prefs = mock(UserPreferenceService.class);
        var level = new EducationLevel(); level.setName("СПО");
        var course = new Course(); course.setName("1 курс");
        var group = new StudyGroup(); group.setId(4L); group.setName("Группа");
        var preference = new UserPreference();
        preference.setEducationLevel(level); preference.setCourse(course); preference.setGroup(group);
        when(prefs.find(MessengerType.MAX, "7")).thenReturn(Optional.of(preference));
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("https://platform-api2.max.ru/updates?timeout=1&limit=100"))
                    .andRespond(withSuccess("""
                            {"updates":[{"update_type":"message_created",
                            "message":{"sender":{"user_id":7},"body":{"text":"/start"}}}]}
                            """, MediaType.APPLICATION_JSON));
            if (i == 0) {
                server.expect(requestTo("https://platform-api2.max.ru/me"))
                        .andExpect(header("Authorization", "test-token"))
                        .andRespond(withSuccess("{\"user_id\":12345678901,\"is_bot\":true,\"username\":\"actual_schedule_bot\"}", MediaType.APPLICATION_JSON));
            }
            server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=7"))
                    .andExpect(header("Authorization", "test-token"))
                    .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].type").value("open_app"))
                    .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].contact_id").value(12345678901L))
                    .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].web_app").value("actual_schedule_bot"))
                    .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].payload").value("g4"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        var bot = new MaxBotService(mock(CatalogService.class), prefs, "test-token", builder.build());
        bot.poll();
        bot.poll();
        server.verify();
    }

    @Test
    void botStartedShowsEducationLevels() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var prefs = mock(UserPreferenceService.class);
        var catalog = mock(CatalogService.class);
        var level = new EducationLevel(); level.setId(2L); level.setName("СПО");
        when(catalog.activeLevels()).thenReturn(java.util.List.of(level));
        when(prefs.find(MessengerType.MAX, "7")).thenReturn(Optional.empty());
        server.expect(requestTo("https://platform-api2.max.ru/updates?timeout=1&limit=100"))
                .andRespond(withSuccess("""
                        {"updates":[{"update_type":"bot_started","chat_id":99,"user":{"user_id":7}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=7"))
                .andExpect(jsonPath("$.text").value("Выберите уровень образования"))
                .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].text").value("СПО"))
                .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].payload").value("L:2"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new MaxBotService(catalog, prefs, "test-token", builder.build()).poll();
        server.verify();
    }

    @Test
    void failedUpdateDoesNotPreventNextUserFromReceivingLevels() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var prefs = mock(UserPreferenceService.class);
        var catalog = mock(CatalogService.class);
        var level = new EducationLevel(); level.setId(2L); level.setName("СПО");
        when(catalog.activeLevels()).thenReturn(java.util.List.of(level));
        server.expect(requestTo("https://platform-api2.max.ru/updates?timeout=1&limit=100"))
                .andRespond(withSuccess("""
                        {"updates":[{"update_type":"bot_started","user":{"user_id":7}},
                        {"update_type":"bot_started","user":{"user_id":8}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=7"))
                .andRespond(withBadRequest());
        server.expect(requestTo("https://platform-api2.max.ru/messages?user_id=8"))
                .andExpect(jsonPath("$.attachments[0].payload.buttons[0][0].payload").value("L:2"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new MaxBotService(catalog, prefs, "test-token", builder.build()).poll();
        server.verify();
    }
}
