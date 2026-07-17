package com.csu.carenest.user.ai;

import com.csu.carenest.user.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class AiSpeechTranscriptionProvider {
    private static final Logger log = LoggerFactory.getLogger(AiSpeechTranscriptionProvider.class);
    private final AiProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiSpeechTranscriptionProvider(AiProviderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.asrTimeout())
                .build();
    }

    public Result transcribe(byte[] audio, String contentType, String fileName) {
        if (properties.asrApiKey().isBlank()) throw new ApiException(503, "语音识别服务尚未配置");
        try {
            String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(audio);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", properties.asrModel(),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of("type", "input_audio",
                                    "input_audio", Map.of("data", dataUrl)))
                    )),
                    "stream", false,
                    "asr_options", Map.of("language", "zh", "enable_itn", true)
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .timeout(properties.asrTimeout())
                    .header("Authorization", "Bearer " + properties.asrApiKey())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI speech provider returned HTTP {}", response.statusCode());
                throw new ApiException(502, "语音识别服务暂时不可用");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String transcript = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (transcript.isBlank()) throw new ApiException(502, "语音识别未返回文字");
            return new Result(transcript, root.path("model").asText(properties.asrModel()));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("AI speech provider call failed: {}", exception.getClass().getSimpleName());
            throw new ApiException(502, "语音识别失败，请重试", exception);
        }
    }

    private URI endpoint() {
        String base = properties.asrEndpoint().replaceAll("/+$", "");
        return URI.create(base.endsWith("/chat/completions") ? base : base + "/chat/completions");
    }

    public record Result(String transcript, String model) {}
}
