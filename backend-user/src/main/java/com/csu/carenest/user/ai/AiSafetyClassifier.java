package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

@Component
public class AiSafetyClassifier {
    public AiProvider.Result classify(String content) {
        String text = content == null ? "" : content.toLowerCase();
        if (contains(text, "胸痛", "胸口", "呼吸困难", "喘不上气", "昏迷", "意识不清", "自杀", "轻生", "摔倒受伤")) {
            return new AiProvider.Result("当前描述可能存在紧急风险。请立即联系家属、平台客服或拨打当地急救电话；我已为你提交紧急协助工单。", "CRITICAL", "EMERGENCY", "URGENT");
        }
        if (contains(text, "药", "剂量", "加量", "减量", "诊断", "处方", "治疗", "血压", "症状")) {
            return new AiProvider.Result("我不能替代医生进行诊断、开药或调整剂量。请联系医生或平台客服获取专业帮助；我已为你提交协助工单。", "WARNING", "MEDICAL_GUIDANCE", "NORMAL");
        }
        return new AiProvider.Result("我可以帮助记录照护需求、提醒事项和联系平台服务。涉及身体不适时，请及时联系家属或专业医护人员。", "NORMAL", "DAILY_CARE", "NORMAL");
    }

    private boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
