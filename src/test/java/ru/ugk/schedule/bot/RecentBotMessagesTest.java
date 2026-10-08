package ru.ugk.schedule.bot;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RecentBotMessagesTest {
    @Test void boundsUsersAndMessages() {
        var history=new RecentBotMessages<String,Integer>(Clock.systemUTC(),2,2,Duration.ofHours(1));
        history.remember("a",1);history.remember("a",2);history.remember("a",3);
        assertThat(history.remove("a")).containsExactly(2,3);
        history.remember("a",1);history.remember("b",1);history.remember("c",1);
        assertThat(history.remove("a")).isEmpty();
        assertThat(history.remove("b")).containsExactly(1);
    }
    @Test void expiresWithoutUserCompletingSelection() {
        var clock=org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.millis()).thenReturn(0L);
        var history=new RecentBotMessages<String,Integer>(clock,2,2,Duration.ofSeconds(1));
        history.remember("a",1);
        org.mockito.Mockito.when(clock.millis()).thenReturn(1000L);
        history.expire();assertThat(history.remove("a")).isEmpty();
    }
    @Test void forgetsOnlySelectedMessage() {
        var history=new RecentBotMessages<String,Integer>();
        history.remember("a",1);history.remember("a",2);history.forget("a",2);
        assertThat(history.remove("a")).containsExactly(1);
    }
}
