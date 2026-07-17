package com.csu.carenest.user.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHealthFeedbackDetectorTest {
    private final AiHealthFeedbackDetector detector = new AiHealthFeedbackDetector();

    @Test
    void mapsSoreThroatToPainAndKeepsOriginalIntensity() {
        AiProvider.Result result = detector.detect("我嗓子疼").orElseThrow();
        assertEquals("PAIN", result.feedbackType());
        assertEquals("MEDIUM", result.feedbackSeverity());
        assertTrue(result.answer().contains("嗓子"));
    }

    @Test
    void mapsStrongPainWordsToHighSeverity() {
        AiProvider.Result result = detector.detect("我头好疼").orElseThrow();
        assertEquals("HIGH", result.feedbackSeverity());
    }
}
