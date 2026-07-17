package com.csu.carenest.user.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import java.time.Duration;

@ConfigurationProperties(prefix = "carenest.ai")
public record AiProviderProperties(String provider, String endpoint, String apiKey, String model, Duration timeout,
                                    String asrEndpoint, String asrApiKey, String asrModel, Duration asrTimeout) {
    public AiProviderProperties(String provider, String endpoint, String apiKey, String model, Duration timeout) {
        this(provider, endpoint, apiKey, model, timeout, endpoint, apiKey, "qwen3-asr-flash", Duration.ofSeconds(20));
    }

    @ConstructorBinding
    public AiProviderProperties {
        provider = value(provider, "rule");
        endpoint = value(endpoint, "https://dashscope.aliyuncs.com/compatible-mode/v1");
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = value(model, "qwen-plus");
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        asrEndpoint = value(asrEndpoint, endpoint);
        asrApiKey = asrApiKey == null || asrApiKey.isBlank() ? apiKey : asrApiKey.trim();
        asrModel = value(asrModel, "qwen3-asr-flash");
        asrTimeout = asrTimeout == null ? Duration.ofSeconds(20) : asrTimeout;
    }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
