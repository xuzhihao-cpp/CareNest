package com.csu.carenest.user.ai;

public abstract class AiProvider {
    public abstract Result answer(String content);
    record Result(String answer, String safetyLevel, String category, String priority) {}
}
