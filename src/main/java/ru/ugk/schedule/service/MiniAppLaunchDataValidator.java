package ru.ugk.schedule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class MiniAppLaunchDataValidator {
    private final String token;
    private final ObjectMapper mapper;

    MiniAppLaunchDataValidator(String token, ObjectMapper mapper) {
        this.token = token;
        this.mapper = mapper;
    }

    // MAX and Telegram use the same bot-token HMAC scheme; tokens stay separate.
    public String userId(String data) {
        try {
            if (token.isBlank() || data == null || data.isBlank() || data.length() > 16384)
                throw new IllegalArgumentException();
            var params = new TreeMap<String, String>();
            for (String part : data.split("&", -1)) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException();
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (params.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
            }
            String hash = params.remove("hash");
            if (hash == null || !hash.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException();
            String signed = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n"));
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token);
            if (!MessageDigest.isEqual(hmac(secret, signed), HexFormat.of().parseHex(hash)))
                throw new IllegalArgumentException();
            long date = Long.parseLong(params.get("auth_date"));
            long now = Instant.now().getEpochSecond();
            if (date < now - 3600 || date > now + 60) throw new IllegalArgumentException();
            var id = mapper.readTree(params.get("user")).path("id");
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0)
                throw new IllegalArgumentException();
            return id.asText();
        } catch (Exception e) {
            // Never echo initData or the bot token in the response or logs.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Mini App launch data");
        }
    }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
