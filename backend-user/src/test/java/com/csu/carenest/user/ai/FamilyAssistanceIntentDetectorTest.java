package com.csu.carenest.user.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyAssistanceIntentDetectorTest {
    private final FamilyAssistanceIntentDetector detector = new FamilyAssistanceIntentDetector();

    @Test
    void detectsDirectRequestsForFamilyHelp() {
        assertTrue(detector.detects("我需要帮助，请联系家属"));
        assertTrue(detector.detects("帮帮我"));
        assertTrue(detector.detects("能帮我叫家人吗"));
    }

    @Test
    void doesNotDetectOrdinaryCareQuestions() {
        assertFalse(detector.detects("肚子疼怎么办"));
        assertFalse(detector.detects("今天怎样安排饮水"));
    }
}
