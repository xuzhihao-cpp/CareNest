package com.csu.carenest.user.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expireHours;
    private final ZoneId zoneId = ZoneId.systemDefault();

    public JwtTokenProvider(
            @Value("${carenest.jwt.secret}") String secret,
            @Value("${carenest.jwt.expire-hours}") long expireHours) {
        this.secretKey = new SecretKeySpec(sha256(secret), "HmacSHA256");
        this.expireHours = expireHours;
    }

    public IssuedToken issue(String userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expireAt = LocalDateTime.now().plusHours(expireHours);
        String token = Jwts.builder()
                .subject(userId)
                .id(sessionId)
                .issuedAt(new Date())
                .expiration(Date.from(expireAt.atZone(zoneId).toInstant()))
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, sessionId, expireAt);
    }

    public ParsedToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedToken(claims.getSubject(), claims.getId());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }
    }

    public String tokenHash(String token) {
        byte[] digest = sha256(token);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record IssuedToken(String token, String sessionId, LocalDateTime expireAt) {
    }

    public record ParsedToken(String userId, String sessionId) {
    }

    static class InvalidTokenException extends RuntimeException {
    }
}
