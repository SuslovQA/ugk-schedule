package ru.ugk.schedule.bot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;

import java.util.*;

@Service
public class TelegramBotService {
    private final RestClient http;
    private final CatalogService catalog;
    private final UserPreferenceService prefs;
    private final String token;
    private final String miniAppUrl;
    private long offset = 0;
    private final Map<Long, Set<Integer>> messages = new HashMap<>();

    public TelegramBotService(CatalogService catalog, UserPreferenceService prefs,
                              @Value("${app.telegram.token:}") String token,
                              @Value("${app.miniapp.url:}") String miniAppUrl, RestClient.Builder builder) {
        this.catalog = catalog;
        this.prefs = prefs;
        this.token = token;
        this.miniAppUrl = miniAppUrl;
        this.http = builder.build();
    }

    @Scheduled(fixedDelayString = "${app.telegram.poll-delay-ms:1500}")
    public void poll() {
        if (token.isBlank()) return;
        try {
            JsonNode root = http.get().uri("https://api.telegram.org/bot" + token + "/getUpdates?timeout=1&offset=" + offset).retrieve().body(JsonNode.class);
            if (root == null || !root.path("ok").asBoolean()) return;
            for (JsonNode u : root.path("result")) {
                offset = Math.max(offset, u.path("update_id").asLong() + 1);
                handle(u);
            }
        } catch (Exception e) {
            System.err.println("Telegram polling: " + e.getMessage());
        }
    }

    private void handle(JsonNode u) {
        if (u.has("message")) {
            JsonNode m = u.path("message");
            String text = m.path("text").asText("");
            String userId = m.path("from").path("id").asText();
            long chatId = m.path("chat").path("id").asLong();
            if ("/start".equals(text) || text.equalsIgnoreCase("start")) {
                remember(chatId, m.path("message_id"));
                showCurrentOrLevels(chatId, userId);
            }
        } else if (u.has("callback_query")) {
            JsonNode q = u.path("callback_query");
            String userId = q.path("from").path("id").asText();
            long chatId = q.path("message").path("chat").path("id").asLong();
            String data = q.path("data").asText();
            answerCallback(q.path("id").asText());
            remember(chatId, q.path("message").path("message_id"));
            processCallback(chatId, userId, data);
        }
    }

    private void processCallback(long chatId, String userId, String data) {
        if (data.equals("RESET")) {
            prefs.reset(MessengerType.TELEGRAM, userId);
            showLevels(chatId);
            return;
        }
        if (data.startsWith("L:")) {
            prefs.setLevel(MessengerType.TELEGRAM, userId, Long.parseLong(data.substring(2)));
            showCourses(chatId, Long.parseLong(data.substring(2)));
            return;
        }
        if (data.startsWith("C:")) {
            prefs.setCourse(MessengerType.TELEGRAM, userId, Long.parseLong(data.substring(2)));
            showGroups(chatId, Long.parseLong(data.substring(2)));
            return;
        }
        if (data.startsWith("G:")) {
            prefs.setGroup(MessengerType.TELEGRAM, userId, Long.parseLong(data.substring(2)));
            clearMessages(chatId);
            showMenu(chatId, userId);
        }
    }

    private void showCurrentOrLevels(long chatId, String userId) {
        var p = prefs.find(MessengerType.TELEGRAM, userId);
        if (p.isPresent() && p.get().getGroup() != null) showMenu(chatId, userId);
        else showLevels(chatId);
    }

    private void showLevels(long chatId) {
        send(chatId, "Выберите уровень образования", callbackRows(catalog.activeLevels().stream().map(x -> new Btn(x.getName(), "L:" + x.getId())).toList()));
    }

    private void showCourses(long chatId, Long levelId) {
        send(chatId, "Выберите курс", callbackRows(catalog.activeCourses(levelId).stream().map(x -> new Btn(x.getName(), "C:" + x.getId())).toList()));
    }

    private void showGroups(long chatId, Long courseId) {
        send(chatId, "Выберите группу / направление", callbackRows(catalog.activeGroups(courseId).stream().map(x -> new Btn(x.getName(), "G:" + x.getId())).toList()));
    }

    private void showMenu(long chatId, String userId) {
        UserPreference p = prefs.find(MessengerType.TELEGRAM, userId).orElseThrow();
        Long gid = p.getGroup().getId();
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        var link = MiniAppLink.forGroup(miniAppUrl, gid);
        link.ifPresent(url -> rows.add(List.of(Map.of("text", "Показать расписание", "web_app", Map.of("url", url)))));
        rows.add(List.of(Map.of("text", "Сброс настроек", "callback_data", "RESET")));
        String message = "Настройки сохранены: " + p.getEducationLevel().getName() + ", " + p.getCourse().getName() + ", " + p.getGroup().getName();
        if (link.isEmpty())
            message += "\nРасписание ещё не опубликовано: администратор должен настроить HTTPS-адрес мини-приложения.";
        send(chatId, message, rows);
    }

    private record Btn(String text, String data) {
    }

    private List<List<Map<String, Object>>> callbackRows(List<Btn> buttons) {
        return buttons.stream().map(b -> List.<Map<String, Object>>of(Map.of("text", b.text(), "callback_data", b.data()))).toList();
    }

    private void send(long chatId, String text, List<List<Map<String, Object>>> rows) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("reply_markup", Map.of("inline_keyboard", rows));
        JsonNode response = http.post().uri("https://api.telegram.org/bot" + token + "/sendMessage").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if (response != null) remember(chatId, response.path("result").path("message_id"));
    }

    private void remember(long chatId, JsonNode id) {
        if (id.isIntegralNumber() && id.asInt() > 0)
            messages.computeIfAbsent(chatId, k -> new LinkedHashSet<>()).add(id.asInt());
    }

    private void clearMessages(long chatId) {
        Set<Integer> ids = messages.remove(chatId);
        if (ids == null) return;
        for (Integer id : ids) {
            try {
                http.post().uri("https://api.telegram.org/bot" + token + "/deleteMessage")
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of("chat_id", chatId, "message_id", id))
                        .retrieve().toBodilessEntity();
            } catch (Exception e) {
                System.err.println("Telegram: could not delete setup message " + id);
            }
        }
    }

    private void answerCallback(String id) {
        try {
            http.post().uri("https://api.telegram.org/bot" + token + "/answerCallbackQuery").contentType(MediaType.APPLICATION_JSON).body(Map.of("callback_query_id", id)).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
        }
    }
}
