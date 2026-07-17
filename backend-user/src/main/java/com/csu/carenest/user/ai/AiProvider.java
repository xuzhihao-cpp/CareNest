package com.csu.carenest.user.ai;

public abstract class AiProvider {
    public abstract Result answer(String content);
    record Result(String answer, String safetyLevel, String category, String priority,
                  String feedbackType, String feedbackSeverity, boolean feedbackRequested) {
        Result(String answer, String safetyLevel, String category, String priority) {
            this(answer, safetyLevel, category, priority, null, null, false);
        }
    }
}
