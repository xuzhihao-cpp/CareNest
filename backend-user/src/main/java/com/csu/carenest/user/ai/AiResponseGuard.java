package com.csu.carenest.user.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class AiResponseGuard {
    private static final Pattern PLATFORM_PHONE = Pattern.compile(
            ".*(?:拨打|电话|热线|客服).{0,12}(?!(?:110|120)(?!\\d))[0-9xX—－-]{5,}.*", Pattern.DOTALL);
    private static final Pattern PLATFORM_HOURS = Pattern.compile(
            ".*(?:平台|客服|服务).{0,20}(?:每天|每日|工作日|周[一二三四五六日天]|早上|上午|中午|下午|晚上|\\d{1,2}[:：点时]|[一二三四五六七八九十]+点).*", Pattern.DOTALL);
    private static final Pattern DOSAGE_INSTRUCTION = Pattern.compile(
            ".*(?:服用|口服|吃|每天|每日|每次|一次|早晚).{0,10}(?:\\d+(?:\\.\\d+)?|一|两|二|三|半).{0,2}(?:片|粒|毫克|克|mg|ml|毫升).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CLINICAL_THRESHOLD_BEFORE_VALUE = Pattern.compile(
            ".*(血压|收缩压|舒张压|血糖|血氧|体温).{0,20}(≥|≤|>|<|高于|低于|大于|小于|超过|少于|达到).{0,4}\\d.*",
            Pattern.DOTALL);
    private static final Pattern CLINICAL_THRESHOLD_AFTER_VALUE = Pattern.compile(
            ".*(?:(?:血压|收缩压|舒张压|血糖|血氧|体温).{0,8}\\d+(?:\\.\\d+)?|\\d{2,3}/\\d{2,3}).{0,8}(?:≥|≤|>|<|以上|以下|以内|以外|高于|低于|大于|小于|超过|少于|属于|诊断).*",
            Pattern.DOTALL);
    private static final Pattern CLINICAL_REFERENCE_VALUE = Pattern.compile(
            ".*(?:血压|收缩压|舒张压).{0,8}\\d{2,3}/\\d{2,3}.*", Pattern.DOTALL);
    private static final Pattern DOSAGE_WITH_FREQUENCY = Pattern.compile(
            ".*\\d+(?:\\.\\d+)?(?:毫克|mg|ml|毫升).{0,10}(?:每天|每日|每次|一天|一日|早晚).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SAFE_MEDICATION_WARNING = Pattern.compile(
            "(?:不要|请勿|不可|不能|不应)(?:自行|擅自|随意|突然)?(?:停药|换药|加药|减药|加量|减量|增加剂量|减少剂量|调整剂量)");

    public Optional<String> rejectionReason(String answer) {
        String compact = answer == null ? "" : answer.replaceAll("\\s+", "");
        String lower = compact.toLowerCase(Locale.ROOT);
        boolean platformContext = lower.contains("carenest") || contains(compact, "平台", "我们");
        if (contains(compact, "24小时", "二十四小时", "全天在线", "全程陪伴", "客服电话", "随时协助")
                || (platformContext && contains(compact,
                        "功能", "支持", "提供", "一键", "自动", "预约", "安排", "联系", "上门", "上传", "录入", "保存", "同步", "照护师", "照护员", "护理员"))
                || contains(compact, "平台照护师", "平台照护员", "照护师录入", "照护员录入")
                || PLATFORM_PHONE.matcher(compact).matches()
                || PLATFORM_HOURS.matcher(compact).matches()) {
            return Optional.of("PLATFORM_CLAIM");
        }
        String decisionText = SAFE_MEDICATION_WARNING.matcher(compact).replaceAll("");
        if (contains(decisionText,
                "你这是", "诊断为", "确诊为", "患有", "考虑是", "可能是", "很可能患", "可能患",
                "停药", "换药", "加药", "减药", "加量", "减量", "调整剂量",
                "建议服用", "可以服用", "应服用", "建议改用")
                || DOSAGE_INSTRUCTION.matcher(decisionText).matches()
                || DOSAGE_WITH_FREQUENCY.matcher(decisionText).matches()
                || CLINICAL_THRESHOLD_BEFORE_VALUE.matcher(decisionText).matches()
                || CLINICAL_THRESHOLD_AFTER_VALUE.matcher(decisionText).matches()
                || CLINICAL_REFERENCE_VALUE.matcher(decisionText).matches()) {
            return Optional.of("MEDICAL_DECISION");
        }
        return Optional.empty();
    }

    private boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
