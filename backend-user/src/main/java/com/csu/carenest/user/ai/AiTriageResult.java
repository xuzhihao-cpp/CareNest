package com.csu.carenest.user.ai;

public record AiTriageResult(
        String level,
        String category,
        String answer,
        boolean followUpRequired,
        String followUpQuestion,
        String priority
) {
    public boolean isCritical() {
        return "CRITICAL".equals(level);
    }

    public boolean isFollowUp() {
        return "FOLLOW_UP".equals(level);
    }
}
