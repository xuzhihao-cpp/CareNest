package com.csu.carenest.user.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "carenest.ai")
public record AiProviderProperties(String provider, String endpoint, String apiKey, String model, Duration timeout) {
    public AiProviderProperties {
        provider = value(provider, "rule");
        endpoint = value(endpoint, "https://dashscope.aliyuncs.com/compatible-mode/v1");
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = value(model, "qwen-plus");
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
    }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
