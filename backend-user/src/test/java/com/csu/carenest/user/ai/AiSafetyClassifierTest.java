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

    @Test
    void letsOrdinaryCareQuestionsReachTheCloudProvider() {
        assertEquals("NORMAL", classifier.classify("血压应该怎么记录？").safetyLevel());
        assertEquals("NORMAL", classifier.classify("忘记吃药怎么办？").safetyLevel());
        assertEquals("NORMAL", classifier.classify("最近肚子痛").safetyLevel());
    }

    @Test
    void blocksExplicitMedicalDecisionsLocally() {
        assertEquals("WARNING", classifier.classify("能不能把降压药加量？").safetyLevel());
        assertEquals("WARNING", classifier.classify("我可以停药吗？").safetyLevel());
        assertEquals("WARNING", classifier.classify("请帮我诊断是什么病").safetyLevel());
        assertEquals("WARNING", classifier.classify("降压药能吃吗？").safetyLevel());
        assertEquals("WARNING", classifier.classify("从一片改成两片行吗？").safetyLevel());
        assertEquals("WARNING", classifier.classify("我是不是得了胃炎？").safetyLevel());
        assertEquals("WARNING", classifier.classify("给我开点降压药").safetyLevel());
        assertEquals("WARNING", classifier.classify("降压药能不能停？").safetyLevel());
        assertEquals("WARNING", classifier.classify("从一片改为两片行吗？").safetyLevel());
        assertEquals("WARNING", classifier.classify("能开始服用阿司匹林吗？").safetyLevel());
        assertEquals("WARNING", classifier.classify("把药量增加一片").safetyLevel());
    }

    @Test
    void blocksClearSelfHarmLanguageLocally() {
        assertEquals("CRITICAL", classifier.classify("我想死").safetyLevel());
        assertEquals("CRITICAL", classifier.classify("我准备割腕").safetyLevel());
        assertEquals("CRITICAL", classifier.classify("我想结束生命").safetyLevel());
        assertEquals("CRITICAL", classifier.classify("我要自残").safetyLevel());
        assertEquals("CRITICAL", classifier.classify("我想结束自己的生命").safetyLevel());
    }

    @Test
    void doesNotTreatAnyChestMentionAsAnEmergency() {
        assertEquals("NORMAL", classifier.classify("胸口长了红疹").safetyLevel());
        assertEquals("CRITICAL", classifier.classify("胸口疼得厉害").safetyLevel());
    }
}
