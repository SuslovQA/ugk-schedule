package ru.ugk.schedule.bot.max;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.ugk.schedule.domain.*;
import ru.ugk.schedule.service.*;

import java.util.*;

@Service
public class MaxBotService {
    private final RestClient http;
    private final CatalogService catalog;
    private final UserPreferenceService prefs;
    private final String token;
    private BotIdentity botIdentity;
    private Long marker;
    private final Map<String, Set<String>> messages = new HashMap<>();

    public MaxBotService(CatalogService catalog, UserPreferenceService prefs, @Value("${app.max.token:}") String token, @Qualifier("maxRestClient") RestClient http) {
        this.catalog = catalog;
        this.prefs = prefs;
        this.token = token;
        this.http = http;
    }

    @Scheduled(fixedDelayString = "${app.max.poll-delay-ms:2000}")
    public void poll() {
        if (token.isBlank()) return;
        try {
            String url = "https://platform-api2.max.ru/updates?timeout=1&limit=100" + (marker == null ? "" : "&marker=" + marker);
            JsonNode root = http.get().uri(url).header("Authorization", token).retrieve().body(JsonNode.class);
            if (root == null) return;
            if (root.hasNonNull("marker")) marker = root.path("marker").asLong();
            for (JsonNode u : root.path("updates")) {
                try {
                    handle(u);
                } catch (Exception e) {
                    System.err.println("MAX update " + u.path("update_type").asText() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("MAX polling: " + e.getMessage());
        }
    }

    private void handle(JsonNode u) {
        String type = u.path("update_type").asText();
        if ("bot_started".equals(type)) {
            String userId = u.path("user").path("user_id").asText("");
            if (!userId.isBlank()) showCurrentOrLevels(userId);
        } else if ("message_created".equals(type)) {
            JsonNode m = u.path("message");
            String userId = firstText(m.path("sender").path("user_id"), u.path("user").path("user_id"));
            String text = m.path("body").path("text").asText("");
            if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("start") || text.equalsIgnoreCase("начать"))
                showCurrentOrLevels(userId);
        } else if ("message_callback".equals(type)) {
            String userId = firstText(u.path("user").path("user_id"), u.path("callback").path("user").path("user_id"));
            remember(userId, u.path("message").path("body").path("mid"));
            String payload = u.path("callback").path("payload").asText();
            processCallback(userId, payload);
        }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode n : nodes)
            if (n != null && !n.isMissingNode() && !n.isNull() && !n.asText().isBlank()) return n.asText();
        return "";
    }

    private void processCallback(String userId, String data) {
        if (data.equals("RESET")) {
            prefs.reset(MessengerType.MAX, userId);
            showLevels(userId);
            return;
        }
        if (data.startsWith("L:")) {
            long id = Long.parseLong(data.substring(2));
            prefs.setLevel(MessengerType.MAX, userId, id);
            showCourses(userId, id);
            return;
        }
        if (data.startsWith("C:")) {
            long id = Long.parseLong(data.substring(2));
            prefs.setCourse(MessengerType.MAX, userId, id);
            showGroups(userId, id);
            return;
        }
        if (data.startsWith("G:")) {
            prefs.setGroup(MessengerType.MAX, userId, Long.parseLong(data.substring(2)));
            clearMessages(userId);
            showMenu(userId);
        }
    }

    private void showCurrentOrLevels(String userId) {
        var p = prefs.find(MessengerType.MAX, userId);
        if (p.isPresent() && p.get().getGroup() != null) showMenu(userId);
        else showLevels(userId);
    }

    private void showLevels(String userId) {
        send(userId, "Выберите уровень образования", buttons(catalog.activeLevels().stream().map(x -> new Btn(x.getName(), "L:" + x.getId())).toList()));
    }

    private void showCourses(String userId, Long levelId) {
        send(userId, "Выберите курс", buttons(catalog.activeCourses(levelId).stream().map(x -> new Btn(x.getName(), "C:" + x.getId())).toList()));
    }

    private void showGroups(String userId, Long courseId) {
        send(userId, "Выберите группу / направление", buttons(catalog.activeGroups(courseId).stream().map(x -> new Btn(x.getName(), "G:" + x.getId())).toList()));
    }

    private void showMenu(String userId) {
        UserPreference p = prefs.find(MessengerType.MAX, userId).orElseThrow();
        BotIdentity bot = currentBot();
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        rows.add(List.of(Map.of("type", "link", "text", "Показать расписание", "url",
                "https://max.ru/" + bot.username() + "?startapp=g" + p.getGroup().getId())));
        rows.add(List.of(Map.of("type", "callback", "text", "Сброс настроек", "payload", "RESET")));
        send(userId, "Настройки сохранены: " + p.getEducationLevel().getName() + ", " + p.getCourse().getName() + ", " + p.getGroup().getName(), rows);
    }

    private record Btn(String text, String data) {
    }

    private record BotIdentity(long id, String username) {
    }

    private BotIdentity currentBot() {
        if (botIdentity == null) {
            JsonNode me = http.get().uri("https://platform-api2.max.ru/me").header("Authorization", token).retrieve().body(JsonNode.class);
            if (me == null || !me.path("user_id").isIntegralNumber() || !me.path("user_id").canConvertToLong() || !me.path("is_bot").asBoolean())
                throw new IllegalStateException("MAX /me did not return a valid bot identity");
            String username = me.path("username").asText("").trim();
            if (username.isBlank())
                throw new IllegalStateException("MAX /me did not return a bot username required for open_app");
            botIdentity = new BotIdentity(me.path("user_id").longValue(), username);
        }
        return botIdentity;
    }

    private List<List<Map<String, Object>>> buttons(List<Btn> bs) {
        return bs.stream().map(b -> List.<Map<String, Object>>of(Map.of("type", "callback", "text", b.text(), "payload", b.data()))).toList();
    }

    private void send(String userId, String text, List<List<Map<String, Object>>> rows) {
        Map<String, Object> body = Map.of("text", text, "attachments", List.of(Map.of("type", "inline_keyboard", "payload", Map.of("buttons", rows))));
        JsonNode response = http.post().uri("https://platform-api2.max.ru/messages?user_id=" + userId).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        if (response != null) remember(userId, response.path("message").path("body").path("mid"));
    }

    private void remember(String userId, JsonNode id) {
        if (!id.asText("").isBlank()) messages.computeIfAbsent(userId, k -> new LinkedHashSet<>()).add(id.asText());
    }

    private void clearMessages(String userId) {
        Set<String> ids = messages.remove(userId);
        if (ids == null) return;
        boolean first = true;
        for (String id : ids) {
            // MAX allows at most two deletions per second in a dialog.
            if (!first) {
                try {
                    Thread.sleep(550);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            first = false;
            try {
                http.delete().uri("https://platform-api2.max.ru/messages?message_id={id}", id)
                        .header("Authorization", token).retrieve().toBodilessEntity();
            } catch (Exception e) {
                System.err.println("MAX: could not delete setup message " + id);
            }
        }
    }
}
