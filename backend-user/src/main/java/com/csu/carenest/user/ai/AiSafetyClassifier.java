package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

@Component
public class AiSafetyClassifier {
    public AiProvider.Result classify(String content) {
        String text = content == null ? "" : content.toLowerCase();
        if (contains(text, "胸痛", "胸口疼", "胸口痛", "胸口闷", "胸口不适", "呼吸困难", "喘不上气", "昏迷", "意识不清", "死亡", "死了",
                "不想活", "活不下去", "想死", "去死", "自杀", "自残", "轻生", "割腕", "结束生命", "结束自己的生命", "伤害自己", "摔倒受伤")) {
            return new AiProvider.Result("当前描述可能存在紧急风险。请立即联系家属、平台客服或拨打当地急救电话；我已为你提交紧急协助工单。", "CRITICAL", "EMERGENCY", "URGENT");
        }
        if (contains(text,
                "加药", "加量", "减药", "减量", "停药", "换药", "改药", "调整剂量",
                "能不能吃", "可以吃什么药", "该吃什么药", "吃多少", "用量",
                "诊断", "确诊", "处方", "治疗方案", "怎么治疗", "如何治疗", "是什么病", "是不是得了", "是不是患",
                "开药", "开点药", "开点降压药", "开始服用", "建议改用")
                || (text.contains("药") && contains(text,
                        "能吃吗", "可以吃吗", "该吃吗", "多吃", "少吃", "几片", "多少片",
                        "能不能停", "该不该停", "可以停吗", "增加", "减少"))
                || (contains(text, "改成", "改为") && contains(text, "片", "粒", "毫克", "mg"))) {
            return new AiProvider.Result("我不能替代医生进行诊断、开药或调整剂量。请保持原医嘱，并联系医生或平台客服获取专业帮助。", "WARNING", "MEDICAL_GUIDANCE", "NORMAL");
        }
        return new AiProvider.Result("我可以帮助记录照护需求、提醒事项和联系平台服务。涉及身体不适时，请及时联系家属或专业医护人员。", "NORMAL", "DAILY_CARE", "NORMAL");
    }

    private boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
