package com.csu.carenest.user.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudAiProviderTest {
    @Test
    void callsOpenAiCompatibleEndpointForNormalCareQuestion() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"请提前准备服务记录和常用物品。\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));
            CloudAiProvider provider = new CloudAiProvider(properties, new AiSafetyClassifier(), new ObjectMapper());

            AiProvider.Result result = provider.answer("上门护理前需要准备什么？");

            assertEquals("NORMAL", result.safetyLevel());
            assertEquals("请提前准备服务记录和常用物品。", result.answer());
            assertTrue(requestBody.get().contains("qwen-plus"));
            assertTrue(requestBody.get().contains("上门护理前需要准备什么"));
            assertTrue(requestBody.get().contains("不能提供诊断"));
            assertTrue(requestBody.get().contains("纯文本"));
            assertTrue(requestBody.get().contains("不得编造电话号码"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackSafelyWhenApiKeyIsMissing() {
        AiProviderProperties properties = new AiProviderProperties(
                "cloud", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", Duration.ofSeconds(2));
        CloudAiProvider provider = new CloudAiProvider(properties, new AiSafetyClassifier(), new ObjectMapper());

        AiProvider.Result result = provider.answer("胸口很闷，呼吸困难");

        assertEquals("CRITICAL", result.safetyLevel());
        assertEquals("URGENT", result.priority());
        assertTrue(result.answer().contains("急救"));
    }
}
