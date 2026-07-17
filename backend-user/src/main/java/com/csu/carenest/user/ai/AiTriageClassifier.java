package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AiTriageClassifier {
    private static final String EMERGENCY_ANSWER =
            "当前描述可能存在紧急风险。请立即联系家属、当地急救或专业医护人员。";
    private static final String WARNING_ANSWER =
            "我不能替代医生进行诊断、开药或调整剂量。请保持原医嘱，并联系医生或专业人员获取帮助。";

    public AiTriageResult classify(String content) {
        String text = content == null ? "" : content.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) return normal();
        if (containsAny(text,
                "呼吸困难", "喘不上气", "胸痛", "胸口疼", "胸口痛", "意识不清",
                "昏厥", "昏迷", "严重摔倒", "摔倒受伤", "自杀", "自残", "想死",
                "不想活", "便血", "呕血", "黑便", "剧烈腹痛", "持续呕吐")) {
            return new AiTriageResult("CRITICAL", "EMERGENCY", EMERGENCY_ANSWER, false, null, "URGENT");
        }
        if (containsAny(text,
                "加药", "加量", "减药", "减量", "停药", "换药", "改药", "调整剂量",
                "能不能停", "能不能吃什么药", "该吃什么药", "吃多少", "用量", "诊断", "确诊",
                "处方", "治疗方案", "怎么治疗", "如何治疗", "是什么病", "开药")) {
            return new AiTriageResult("WARNING", "MEDICAL_GUIDANCE", WARNING_ANSWER, false, null, "NORMAL");
        }
        if (containsAny(text, "肚子疼", "腹痛", "胃疼", "胃痛")) {
            return followUp("ABDOMINAL_PAIN", "请问疼痛持续多久，严重程度如何？是否伴有发热、呕吐或便血？");
        }
        if (containsAny(text, "头晕", "眩晕", "站不稳")) {
            return followUp("DIZZINESS", "请问头晕持续多久？是否伴有昏厥、胸痛、呼吸困难或一侧无力？");
        }
        if (containsAny(text, "发烧", "发热", "体温高")) {
            return followUp("FEVER", "请问目前体温是多少，持续多久？是否伴有寒战、呼吸困难或意识不清？");
        }
        if (containsAny(text, "呕吐", "恶心")) {
            return followUp("VOMITING", "请问呕吐持续多久，能否喝水？是否有剧烈腹痛、便血或明显乏力？");
        }
        if (containsAny(text, "咳嗽", "咳痰")) {
            return followUp("COUGH", "请问咳嗽持续多久？是否伴有呼吸困难、胸痛或高热？");
        }
        if (containsAny(text, "乏力", "没力气", "浑身无力")) {
            return followUp("FATIGUE", "请问乏力从什么时候开始？是否伴有发热、胸痛、呼吸困难或意识异常？");
        }
        if (containsAny(text, "摔倒", "跌倒", "磕到了")) {
            return followUp("FALL", "请问有没有撞到头、明显疼痛、出血、无法站立或意识不清？");
        }
        return normal();
    }

    private AiTriageResult followUp(String category, String question) {
        return new AiTriageResult("FOLLOW_UP", category, question, true, question, "NORMAL");
    }

    private AiTriageResult normal() {
        return new AiTriageResult(
                "NORMAL",
                "DAILY_CARE",
                "我可以帮助你处理日常照护、生活安排和提醒事项。",
                false,
                null,
                "NORMAL"
        );
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
