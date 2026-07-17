package com.csu.carenest.user.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "provider", havingValue = "cloud")
public class CloudAiProvider extends AiProvider {
    private static final Logger log = LoggerFactory.getLogger(CloudAiProvider.class);
    private static final String SYSTEM_PROMPT = "你是 CareNest 的日常照护助手。只回答日常照护、生活支持和一般健康管理问题，不能提供诊断、处方、用药剂量调整或替代医生的判断。遇到明确紧急症状，建议立即联系家属、当地急救或专业医护人员。请严格只输出 JSON，不要 Markdown，格式为 {\"answer\":\"给老人的安全回答\",\"feedback\":{\"shouldSubmit\":false,\"feedbackType\":null,\"severity\":null}}。当用户明确描述疼痛、头晕、睡眠、饮食或精神状态变化时，将 shouldSubmit 设为 true，并从 PAIN、DIZZINESS、SLEEP、DIET、MENTAL_STATE 中选择 feedbackType，从 LOW、MEDIUM、HIGH 中选择 severity；不要把提问、泛泛咨询或没有具体不适的内容作为健康反馈。feedback 不得包含诊断。回答简洁、清楚、适合长辈阅读，只输出纯文本内容。不得编造电话号码、地址、服务时间或平台功能，不得虚构 CareNest 功能，不得给出具体临床阈值、诊断标准或用药数量。";
    private final AiProviderProperties properties;
    private final AiSafetyClassifier classifier;
    private final AiResponseGuard responseGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudAiProvider(AiProviderProperties properties, AiSafetyClassifier classifier, AiResponseGuard responseGuard, ObjectMapper objectMapper) {
        this.properties = properties;
        this.classifier = classifier;
        this.responseGuard = responseGuard;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
    }

    @Override public Result answer(String content) {
        Result safety = classifier.classify(content);
        // Safety decisions are deterministic. A cloud response must never downgrade
        // medication, diagnosis or emergency requests classified by local rules.
        if (!"NORMAL".equals(safety.safetyLevel())) return safety;
        Result fallback = classifier.classify("");
        if (properties.apiKey().isBlank()) return fallback;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", properties.model(),
                    "messages", List.of(Map.of("role", "system", "content", SYSTEM_PROMPT), Map.of("role", "user", "content", content)),
                    "temperature", 0.3));
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .timeout(properties.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI provider returned HTTP {}", response.statusCode());
                return fallback;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String modelContent = root.path("choices").path(0).path("message").path("content").asText("").trim();
            JsonNode structured = parseJson(modelContent);
            String answer = structured == null ? modelContent : structured.path("answer").asText("").trim();
            var rejection = responseGuard.rejectionReason(answer);
            if (answer.isEmpty() || rejection.isPresent()) {
                rejection.ifPresent(reason -> log.warn("AI provider response rejected: {}", reason));
                return fallback;
            }
            JsonNode feedback = structured == null ? null : structured.path("feedback");
            boolean requested = feedback != null && feedback.path("shouldSubmit").asBoolean(false);
            String feedbackType = requested ? feedback.path("feedbackType").asText("") : null;
            String feedbackSeverity = requested ? feedback.path("severity").asText("") : null;
            if (!isValidFeedback(feedbackType, feedbackSeverity)) {
                requested = false;
                feedbackType = null;
                feedbackSeverity = null;
            }
            return new Result(answer, "NORMAL", "DAILY_CARE", "NORMAL", feedbackType, feedbackSeverity, requested);
        } catch (Exception exception) {
            log.warn("AI provider call failed: {}", exception.getClass().getSimpleName());
            return fallback;
        }
    }

    private JsonNode parseJson(String content) {
        try {
            String normalized = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
            JsonNode node = objectMapper.readTree(normalized);
            return node != null && node.isObject() && node.has("answer") ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidFeedback(String type, String severity) {
        return type != null && severity != null
                && List.of("PAIN", "DIZZINESS", "SLEEP", "DIET", "MENTAL_STATE").contains(type)
                && List.of("LOW", "MEDIUM", "HIGH").contains(severity);
    }

    private URI endpoint() {
        String base = properties.endpoint().replaceAll("/+$", "");
        return URI.create(base.endsWith("/chat/completions") ? base : base + "/chat/completions");
    }

}
