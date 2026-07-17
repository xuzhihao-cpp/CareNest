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

    public static String nurseRecommendationKey(String requestHash) {
        return nurseRecommendationPrefix() + requestHash;
    }

    public static String nurseRecommendationPrefix() {
        return "recommend:nurses:";
    }

    public static String trainingRecommendationKey(String orderId, String nurseId) {
        return trainingRecommendationPrefix() + userHash(orderId + ":" + nurseId) + ":v1";
    }

    public static String trainingRecommendationPrefix() {
        return "carenest:training:recommendation:";
    }

    public static String basicDashboardKey(String from, String to) {
        return basicDashboardPrefix() + from + ":" + to + ":v1";
    }

    public static String basicDashboardPrefix() {
        return "carenest:dashboard:basic:";
    }

    public static String qualityDashboardKey(String from, String to) {
        return qualityDashboardPrefix() + from + ":" + to + ":v1";
    }

    public static String qualityDashboardPrefix() {
        return "carenest:dashboard:quality:";
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
