package com.csu.carenest.careadmin.auth;

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

/**
 * JWT 解析工具，密钥和算法与 backend-user 保持一致，便于两个后端共享登录态。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${carenest.jwt.secret}") String secret) {
        this.secretKey = new SecretKeySpec(sha256(secret), "HmacSHA256");
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

    public record ParsedToken(String userId, String sessionId) {
    }

    public static class InvalidTokenException extends RuntimeException {
    }
}
