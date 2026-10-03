package com.skylanka.air.shared.security;

import com.skylanka.air.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.ttl-minutes:60}") long ttlMinutes) {
        if (secret == null || secret.length() < 24) {
            throw new IllegalArgumentException("JWT secret must contain at least 24 characters.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(60, ttlMinutes * 60);
    }

    public String issue(User user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(user.getId()));
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("iat", now);
        claims.put("exp", now + ttlSeconds);

        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(toJson(claims));
        String unsigned = header + "." + payload;
        return unsigned + "." + sign(unsigned);
    }

    public Map<String, String> validate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing token.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
            throw new IllegalArgumentException("Invalid token signature.");
        }
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> claims = new HashMap<>();
        for (String pair : json.replace("{", "").replace("}", "").split(",")) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                claims.put(unquote(keyValue[0]), unquote(keyValue[1]));
            }
        }
        long expiry = Long.parseLong(claims.getOrDefault("exp", "0"));
        if (expiry <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Token has expired.");
        }
        if (!claims.containsKey("sub") || !claims.containsKey("role")) {
            throw new IllegalArgumentException("Token is missing required claims.");
        }
        return claims;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign JWT.", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(Map<String, Object> values) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) result.append(value);
            else result.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
        }
        return result.append('}').toString();
    }

    private String unquote(String value) {
        String cleaned = value.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.replace("\\\"", "\"");
    }
}