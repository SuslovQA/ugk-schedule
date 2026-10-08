package ru.ugk.schedule.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

/** Durable inbox. One poller per token; external message delivery is at least once. */
@Service
public class BotUpdateInbox {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BotUpdateInbox.class);
    public record Update(long id, JsonNode payload) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public BotUpdateInbox(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public static String stream(String messenger, String token) { return messenger + ":" + digest(token); }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Long cursor(String stream) {
        List<Long> result=jdbc.query("select cursor_value from bot_polling_state where stream=?",
                (rs,row) -> (Long)rs.getObject(1),stream);
        return result.isEmpty() ? null : result.get(0);
    }
    @Transactional
    public void capture(String stream, Long cursor, JsonNode updates) {
        if (!updates.isArray() || updates.size() > 100) throw new IllegalArgumentException("Invalid update batch");
        jdbc.update("insert into bot_polling_state(stream) values (?) on conflict do nothing",stream);
        // Reject new ingestion before acknowledging when the operator must clear a backlog.
        Long count=jdbc.queryForObject("select count(*) from bot_updates where stream=? and completed_at is null",Long.class,stream);
        if (count != null && count + updates.size() > 10000) throw new IllegalStateException("Bot inbox backlog limit reached");
        for (JsonNode update : updates) {
            String payload=update.toString();
            String key=update.has("update_id") ? update.path("update_id").asText() : digest(payload);
            String user=userKey(update);
            jdbc.update("insert into bot_updates(stream,event_key,user_key,payload) values (?,?,?,?) on conflict do nothing",
                    stream,key,user,payload);
        }
        if (cursor != null) jdbc.update("update bot_polling_state set cursor_value=? where stream=?",cursor,stream);
        jdbc.update("delete from bot_updates where stream=? and completed_at < current_timestamp - interval '7 days'",stream);
    }
    private String userKey(JsonNode u) {
        for (JsonNode id : List.of(u.path("message").path("from").path("id"),
                u.path("callback_query").path("from").path("id"),u.path("user").path("user_id"),
                u.path("callback").path("user").path("user_id"),u.path("message").path("sender").path("user_id")))
            if (!id.isMissingNode() && !id.isNull()) return id.asText();
        return "unknown";
    }
    public List<Update> pending(String stream, int limit) {
        return jdbc.query("""
                select u.id,u.payload from bot_updates u
                where u.stream=? and u.completed_at is null and u.attempts<5 and u.available_at<=current_timestamp
                and not exists (select 1 from bot_updates earlier where earlier.stream=u.stream
                    and earlier.user_key=u.user_key and earlier.id<u.id and earlier.completed_at is null and earlier.attempts<5)
                order by u.id limit ?
                """,(rs,row) -> {
                    try { return new Update(rs.getLong(1),mapper.readTree(rs.getString(2))); }
                    catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored bot update",e); }
                },stream,limit);
    }
    public void complete(long id) {
        // Erase incoming user data on success; keep the deduplication key for seven days.
        jdbc.update("update bot_updates set completed_at=current_timestamp,payload=null,last_failure=null where id=?",id);
    }
    public void failed(long id, Exception failure) {
        int seconds=30;
        if (failure instanceof RestClientResponseException response && response.getStatusCode().value()==429) {
            try {
                String header=response.getResponseHeaders()==null ? null : response.getResponseHeaders().getFirst("Retry-After");
                if (header!=null) seconds=Math.max(seconds,Integer.parseInt(header));
                JsonNode body=mapper.readTree(response.getResponseBodyAsString());
                seconds=Math.max(seconds,body.path("parameters").path("retry_after").asInt(30));
            } catch (Exception ignored) { /* Retain the conservative default delay. */ }
        }
        jdbc.update("update bot_updates set attempts=attempts+1,available_at=?,last_failure=? where id=?",
                Timestamp.from(Instant.now().plusSeconds(seconds)),failure.getClass().getSimpleName(),id);
        log.warn("Bot update id={} failed; scheduled retry or dead-letter after five attempts; type={}",
                id,failure.getClass().getSimpleName());
    }
    public void drain(String stream, Consumer<JsonNode> handler) {
        for (int processed=0;processed<100;) {
            List<Update> batch=pending(stream,100-processed);
            if (batch.isEmpty()) return;
            for (Update update : batch) {
                processed++;
                try { handler.accept(update.payload()); complete(update.id()); }
                catch (Exception failure) { failed(update.id(),failure); }
            }
        }
    }
}
