package com.csu.carenest.user.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSafetyClassifierTest {
    private final AiSafetyClassifier classifier = new AiSafetyClassifier();

    @Test
    void treatsDeathQuestionAsCriticalRisk() {
        AiProvider.Result result = classifier.classify("死了怎么办");

        assertEquals("CRITICAL", result.safetyLevel());
        assertEquals("URGENT", result.priority());
        assertTrue(result.answer().contains("急救"));
    }
}
