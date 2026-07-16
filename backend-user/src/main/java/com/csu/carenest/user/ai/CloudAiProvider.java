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
    private static final String SYSTEM_PROMPT = "你是 CareNest 养老照护助手。只回答日常照护、平台服务和生活支持问题。不能提供诊断、处方、用药剂量调整或替代医生的判断。遇到紧急症状必须建议立即联系家属、平台客服或当地急救。回答简洁、明确、适合长辈阅读。";
    private final AiProviderProperties properties;
    private final AiSafetyClassifier classifier;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudAiProvider(AiProviderProperties properties, AiSafetyClassifier classifier, ObjectMapper objectMapper) {
        this.properties = properties;
        this.classifier = classifier;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    @Override public Result answer(String content) {
        Result safety = classifier.classify(content);
        if (!"NORMAL".equals(safety.safetyLevel()) || properties.apiKey().isBlank()) return safety;
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
                return safety;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return answer.isEmpty() ? safety : new Result(answer, "NORMAL", "DAILY_CARE", "NORMAL");
        } catch (Exception exception) {
            log.warn("AI provider call failed: {}", exception.getClass().getSimpleName());
            return safety;
        }
    }

    private URI endpoint() {
        String base = properties.endpoint().replaceAll("/+$", "");
        return URI.create(base.endsWith("/chat/completions") ? base : base + "/chat/completions");
    }
}
