package com.csu.carenest.careadmin.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RedisKeyFactory {

    private RedisKeyFactory() {
    }

    public static String onShelfServiceItemsKey() {
        return "carenest:service-items:on-shelf:v1";
    }

    public static String homeKey(String role, String userId) {
        return "carenest:home:" + role + ":" + userHash(userId) + ":v1";
    }

    public static String orderLockKey(String orderId) {
        return "carenest:lock:order:" + orderId;
    }

    public static String reportLockKey(String reportId) {
        return "carenest:lock:report:" + reportId;
    }

    public static String archiveLockKey(String taskId) {
        return "carenest:lock:archive:" + taskId;
    }

    public static String userHash(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(userId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append(String.format("%02x", value));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
