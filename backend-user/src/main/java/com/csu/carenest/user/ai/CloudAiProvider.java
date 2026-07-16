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
    private static final String SYSTEM_PROMPT = "你是 CareNest 的日常照护助手。只回答日常照护、生活支持和一般健康管理问题，不能提供诊断、处方、用药剂量调整或替代医生的判断。遇到明确紧急症状，建议立即联系家属、当地急救或专业医护人员。回答简洁、清楚、适合长辈阅读，只输出纯文本，不使用 Markdown、表格、Emoji 或特殊符号。除非用户明确询问平台功能，不要提及 CareNest、平台客服、平台安排、平台服务、记录需求或任何未确认的功能；直接围绕用户的问题给出安全、可执行的日常建议。不得编造电话号码、地址、服务时间或平台尚未提供的信息。不得虚构 CareNest 功能、服务能力或工作人员安排。不得给出具体临床阈值、诊断标准或用药数量。";
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
        if ("CRITICAL".equals(safety.safetyLevel())) return safety;
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
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            var rejection = responseGuard.rejectionReason(answer);
            if (answer.isEmpty() || rejection.isPresent()) {
                rejection.ifPresent(reason -> log.warn("AI provider response rejected: {}", reason));
                return fallback;
            }
            return new Result(answer, "NORMAL", "DAILY_CARE", "NORMAL");
        } catch (Exception exception) {
            log.warn("AI provider call failed: {}", exception.getClass().getSimpleName());
            return fallback;
        }
    }

    private URI endpoint() {
        String base = properties.endpoint().replaceAll("/+$", "");
        return URI.create(base.endsWith("/chat/completions") ? base : base + "/chat/completions");
    }

}
