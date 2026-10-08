package ru.ugk.schedule.bot.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramBotServiceTest {
    @Test
    void backFromGroupsDeletesCurrentMessageAndShowsCourses() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var catalog = mock(CatalogService.class);
        var course = new Course(); course.setId(3L); course.setName("1 курс");
        when(catalog.activeCourses(2L)).thenReturn(java.util.List.of(course));
        server.expect(requestTo("https://api.telegram.org/bottest/getUpdates?timeout=1&offset=0"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[{"update_id":1,"callback_query":{"id":"back","from":{"id":7},
                        "message":{"message_id":13,"chat":{"id":7}},"data":"BACK:COURSES:2"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/bottest/answerCallbackQuery"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/bottest/deleteMessage"))
                .andExpect(jsonPath("$.message_id").value(13))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andExpect(jsonPath("$.text").value("Выберите курс"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("C:3"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[1][0].callback_data").value("BACK:LEVELS"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new TelegramBotService(catalog, mock(UserPreferenceService.class), "test", "", builder,
                new ru.ugk.schedule.bot.TestInbox()).poll();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clearsSetupBeforeMenuEvenIfOneDeletionFails(boolean deletionFails) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        var prefs = mock(UserPreferenceService.class);
        var level = new EducationLevel(); level.setId(2L); level.setName("СПО");
        var course = new Course(); course.setName("1 курс");
        var group = new StudyGroup(); group.setId(4L); group.setName("Группа");
        var preference = new UserPreference();
        preference.setEducationLevel(level); preference.setCourse(course); preference.setGroup(group);
        when(prefs.find(MessengerType.TELEGRAM, "7")).thenReturn(Optional.empty(), Optional.of(preference));
        server.expect(requestTo("https://api.telegram.org/bottest/getUpdates?timeout=1&offset=0"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[
                        {"update_id":1,"message":{"message_id":10,"text":"/start","from":{"id":7},"chat":{"id":7}}},
                        {"update_id":2,"callback_query":{"id":"a","from":{"id":7},"message":{"message_id":11,"chat":{"id":7}},"data":"L:2"}},
                        {"update_id":3,"callback_query":{"id":"b","from":{"id":7},"message":{"message_id":12,"chat":{"id":7}},"data":"C:3"}},
                        {"update_id":4,"callback_query":{"id":"c","from":{"id":7},"message":{"message_id":13,"chat":{"id":7}},"data":"G:4"}}]}
                        """, MediaType.APPLICATION_JSON));
        String[] texts = {"Выберите уровень образования", "Выберите курс", "Выберите группу / направление"};
        String[] backPayloads = {null, "BACK:LEVELS", "BACK:COURSES:2"};
        for (int index = 0; index < texts.length; index++) {
            var expectation = server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                    .andExpect(jsonPath("$.text").value(texts[index]));
            if (backPayloads[index] != null)
                expectation.andExpect(jsonPath("$.reply_markup.inline_keyboard[-1][0].text").value("Назад"))
                        .andExpect(jsonPath("$.reply_markup.inline_keyboard[-1][0].callback_data").value(backPayloads[index]));
            expectation.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":" + (index + 11) + "}}", MediaType.APPLICATION_JSON));
        }
        for (int id = 11; id <= 13; id++) {
            server.expect(requestTo("https://api.telegram.org/bottest/answerCallbackQuery"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        }
        for (int id = 10; id <= 13; id++) {
            server.expect(requestTo("https://api.telegram.org/bottest/deleteMessage"))
                    .andExpect(jsonPath("$.chat_id").value(7))
                    .andExpect(jsonPath("$.message_id").value(id))
                    .andRespond(deletionFails && id == 10 ? withBadRequest() : withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        }
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andExpect(jsonPath("$.text").value("Настройки сохранены: СПО, 1 курс, Группа"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard.length()").value(2))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].text").value("Показать расписание"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[1][0].callback_data").value("RESET"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new TelegramBotService(mock(CatalogService.class), prefs, "test", "https://schedule.ru/app", builder, new ru.ugk.schedule.bot.TestInbox()).poll();
        verify(prefs).setGroup(MessengerType.TELEGRAM, "7", 4L);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://YOUR_HTTPS_DOMAIN/miniapp/schedule", "https://example.com/miniapp/schedule", "http://schedule.ru/app", "https://localhost/app", "not a url"})
    void rejectsUnconfiguredOrInvalidLinks(String url) {
        assertThat(MiniAppLink.forGroup(url, 4L)).isEmpty();
    }

    @Test
    void preservesQueryAndReplacesGroup() {
        assertThat(MiniAppLink.forGroup("https://schedule.ru/app?lang=ru&groupId=2", 4L))
                .contains("https://schedule.ru/app?lang=ru&groupId=4");
    }

    @Test
    void placeholderDoesNotSendInvalidWebAppButton() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var prefs = mock(UserPreferenceService.class);
        var level = new EducationLevel(); level.setName("СПО");
        var course = new Course(); course.setName("1 курс");
        var group = new StudyGroup(); group.setId(4L); group.setName("Группа");
        var preference = new UserPreference();
        preference.setEducationLevel(level); preference.setCourse(course); preference.setGroup(group);
        when(prefs.find(MessengerType.TELEGRAM, "7")).thenReturn(Optional.of(preference));
        server.expect(requestTo("https://api.telegram.org/bottest/getUpdates?timeout=1&offset=0"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":{\"text\":\"/start\",\"from\":{\"id\":7},\"chat\":{\"id\":7}}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("RESET"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].web_app").doesNotExist())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("ещё не опубликовано")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        new TelegramBotService(mock(CatalogService.class), prefs, "test",
                "https://YOUR_HTTPS_DOMAIN/miniapp/schedule", builder, new ru.ugk.schedule.bot.TestInbox()).poll();
        server.verify();
    }
}
