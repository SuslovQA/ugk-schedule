package ru.ugk.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.ugk.schedule.controller.api.TelegramMiniAppController;
import ru.ugk.schedule.domain.MessengerType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TelegramLaunchDataServiceTest {
    private final TelegramLaunchDataService validator = new TelegramLaunchDataService("test-token", new ObjectMapper());

    private String signed(long date, String user) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secret = mac.doFinal("test-token".getBytes(StandardCharsets.UTF_8));
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(
                ("auth_date=" + date + "\nuser=" + user).getBytes(StandardCharsets.UTF_8)));
        return "user=" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "&hash=" + hash + "&auth_date=" + date;
    }

    @Test
    void validatesSignatureAndDecodesUnicode() throws Exception {
        assertEquals("7", validator.userId(signed(Instant.now().getEpochSecond(), "{\"id\":7,\"first_name\":\"Иван + A=B\"}")));
    }

    @Test
    void rejectsTamperingDuplicatesExpiredAndFutureData() throws Exception {
        long now = Instant.now().getEpochSecond();
        String valid = signed(now, "{\"id\":7}");
        for (String data : new String[]{valid.replace("%3A7", "%3A8"), valid + "&user=%7B%22id%22%3A8%7D",
                valid + "&hash=00", signed(now - 3601, "{\"id\":7}"), signed(now + 120, "{\"id\":7}"),
                signed(now, "{}"), "user=7", "user=%ZZ&hash=00"}) {
            assertEquals(401, assertThrows(ResponseStatusException.class, () -> validator.userId(data)).getStatusCode().value());
        }
        assertThrows(ResponseStatusException.class,
                () -> new TelegramLaunchDataService("other-token", new ObjectMapper()).userId(valid));
    }

    @Test
    void builtInLaunchReturnsOnlyAuthenticatedUsersSavedGroup() throws Exception {
        var prefs = mock(UserPreferenceService.class);
        when(prefs.selectedGroupId(MessengerType.TELEGRAM, "7")).thenReturn(Optional.of(42L), Optional.empty());
        var mvc = standaloneSetup(new TelegramMiniAppController(validator, prefs)).build();
        String body = new ObjectMapper().writeValueAsString(
                new TelegramMiniAppController.LaunchRequest(signed(Instant.now().getEpochSecond(), "{\"id\":7}")));
        mvc.perform(post("/api/public/telegram/group").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.groupId").value(42));
        mvc.perform(post("/api/public/telegram/group").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().json("{\"groupId\":null}"));
        verify(prefs, times(2)).selectedGroupId(MessengerType.TELEGRAM, "7");
        clearInvocations(prefs);
        mvc.perform(post("/api/public/telegram/group").contentType("application/json")
                        .content("{\"initData\":\"user=7&hash=bad\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(prefs);
    }
}


