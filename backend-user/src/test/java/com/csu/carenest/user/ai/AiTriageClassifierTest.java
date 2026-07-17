package com.csu.carenest.user.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTriageClassifierTest {
    private final AiTriageClassifier classifier = new AiTriageClassifier();

    @Test
    void abdominalPainRequestsClarificationWithoutCreatingRisk() {
        AiTriageResult result = classifier.classify("我肚子疼");

        assertEquals("FOLLOW_UP", result.level());
        assertEquals("ABDOMINAL_PAIN", result.category());
        assertTrue(result.followUpRequired());
        assertNotNull(result.followUpQuestion());
    }

    @Test
    void breathingDifficultyIsImmediatelyCritical() {
        AiTriageResult result = classifier.classify("老人呼吸困难，喘不上气");

        assertEquals("CRITICAL", result.level());
        assertEquals("EMERGENCY", result.category());
        assertEquals("URGENT", result.priority());
    }

    @Test
    void medicationQuestionIsWarning() {
        AiTriageResult result = classifier.classify("这个药能不能停");

        assertEquals("WARNING", result.level());
        assertEquals("MEDICAL_GUIDANCE", result.category());
    }
}
