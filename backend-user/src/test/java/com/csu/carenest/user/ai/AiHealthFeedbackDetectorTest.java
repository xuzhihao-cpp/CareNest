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

    @Test
    void mapsOtherPainLocationsToPainFeedback() {
        for (String content : new String[]{"我牙疼", "我肩膀疼", "我脖子疼", "我胳膊疼", "我膝盖疼", "我脚疼", "我腰疼", "我背疼", "我全身疼"}) {
            AiProvider.Result result = detector.detect(content).orElseThrow();
            assertEquals("PAIN", result.feedbackType(), content);
        }
    }

    @Test
    void mapsPainWithoutLocationToPainFeedback() {
        assertEquals("PAIN", detector.detect("我疼").orElseThrow().feedbackType());
    }
}
