package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class AiHealthFeedbackDetector {
    public Optional<AiProvider.Result> detect(String content) {
        String text = content == null ? "" : content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (contains(text, "疼", "痛", "不舒服", "不适") && contains(text, "嗓子", "喉咙", "咽喉", "头", "肚子", "腹", "胸", "腿", "腰", "背")) {
            String location = contains(text, "嗓子", "喉咙", "咽喉") ? "嗓子" : contains(text, "头") ? "头部" : contains(text, "肚子", "腹") ? "腹部" : "身体";
            return Optional.of(result("PAIN", severity(text), "听起来你的" + location + "不舒服。请先休息并留意变化，如果持续加重或影响正常活动，请联系家属或专业医护人员。"));
        }
        if (contains(text, "头晕", "眩晕", "站不稳")) {
            return Optional.of(result("DIZZINESS", severity(text), "你现在有头晕不适，请先坐下或躺下休息，起身时动作慢一些；如果持续或加重，请联系家属或专业医护人员。"));
        }
        if (contains(text, "失眠", "睡不着", "睡不好", "夜里总醒")) {
            return Optional.of(result("SLEEP", severity(text), "我记下了你的睡眠变化。今晚可以尽量保持安静和规律作息，如果连续多天没有改善，请联系家属或专业医护人员。"));
        }
        return Optional.empty();
    }

    private AiProvider.Result result(String type, String severity, String answer) {
        return new AiProvider.Result(answer, "NORMAL", "DAILY_CARE", "NORMAL", type, severity, true);
    }

    private String severity(String text) {
        if (contains(text, "剧烈", "非常", "特别", "好疼", "很疼", "疼得厉害", "痛得厉害")) return "HIGH";
        if (contains(text, "有点", "一点", "轻微", "稍微")) return "LOW";
        return "MEDIUM";
    }

    private boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
