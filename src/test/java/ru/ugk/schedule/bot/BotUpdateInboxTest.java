package ru.ugk.schedule.bot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BotUpdateInboxTest {
    @Test void oneFailureDoesNotDiscardOtherUsers() throws Exception {
        var inbox=spy(new TestInbox());
        inbox.capture("test",4L,new ObjectMapper().readTree("[{\"user\":1},{\"user\":2}]"));
        inbox.drain("test",u -> { if(u.path("user").asInt()==1) throw new IllegalStateException(); });
        verify(inbox).failed(eq(1L),any(IllegalStateException.class));
        verify(inbox).complete(2L);
    }
    @Test void failedDatabaseCaptureCannotAdvanceCursor() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));
        var inbox=new BotUpdateInbox(jdbc,new ObjectMapper());
        assertThatThrownBy(() -> inbox.capture("test",9L,new ObjectMapper().readTree("[]")))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(jdbc,never()).update(eq("update bot_polling_state set cursor_value=? where stream=?"),any(Object[].class));
    }
    @Test void streamUsesTokenFingerprintAndSeparatesMessengers() {
        assertThat(BotUpdateInbox.stream("telegram","test-secret")).doesNotContain("test-secret")
            .isNotEqualTo(BotUpdateInbox.stream("max","test-secret"));
    }
}
