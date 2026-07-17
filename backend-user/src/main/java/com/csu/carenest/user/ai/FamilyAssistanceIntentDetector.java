package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FamilyAssistanceIntentDetector {
    private static final String[] PHRASES = {
            "联系家属", "联系家人", "叫家属", "叫家人", "通知家属", "通知家人",
            "帮我联系", "帮我叫", "我需要帮助", "我需要帮忙", "我需要人帮",
            "帮帮我", "救救我", "有人吗", "找家属", "找家人"
    };

    public boolean detects(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String phrase : PHRASES) {
            if (normalized.contains(phrase)) return true;
        }
        return false;
    }
}
