package com.csu.carenest.user.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudAiProviderTest {
    @Test
    void callsOpenAiCompatibleEndpointForEveryNonCriticalQuestion() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
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
            CloudAiProvider provider = provider(properties);

            for (String question : new String[]{"血压应该怎么记录？", "我可以停药吗？", "这是什么病？"}) {
                AiProvider.Result result = provider.answer(question);
                assertEquals("NORMAL", result.safetyLevel());
                assertEquals("请提前准备服务记录和常用物品。", result.answer());
            }
            assertTrue(requestBody.get().contains("qwen-plus"));
            assertTrue(requestBody.get().contains("这是什么病？"));
            assertTrue(requestBody.get().contains("不能提供诊断"));
            assertTrue(requestBody.get().contains("纯文本"));
            assertTrue(requestBody.get().contains("不得编造电话号码"));
            assertTrue(requestBody.get().contains("不得虚构 CareNest 功能"));
            assertTrue(requestBody.get().contains("具体临床阈值"));
            assertEquals(3, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToNormalWhenWarningQuestionHasNoApiKey() {
        AiProviderProperties properties = new AiProviderProperties(
                "cloud", "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus", Duration.ofSeconds(2));
        CloudAiProvider provider = provider(properties);

        AiProvider.Result result = provider.answer("我可以停药吗？");

        assertEquals("NORMAL", result.safetyLevel());
        assertEquals("DAILY_CARE", result.category());
    }

    @Test
    void rejectsUnsupportedPlatformPromisesFromCloudResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"CareNest会全程陪伴，平台客服24小时在线，请拨打400-XXX-XXXX。\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));
            CloudAiProvider provider = provider(properties);

            AiProvider.Result result = provider.answer("我可以停药吗？");

            assertEquals("NORMAL", result.safetyLevel());
            assertFalse(result.answer().contains("24小时"));
            assertFalse(result.answer().contains("400-"));
            assertFalse(result.answer().contains("全程陪伴"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMedicationDecisionFromCloudResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"建议立即停药，每天服用两片其他药物。\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));

            AiProvider.Result result = provider(properties).answer("我可以停药吗？");

            assertEquals("NORMAL", result.safetyLevel());
            assertFalse(result.answer().contains("停药"));
            assertFalse(result.answer().contains("两片"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToNormalWhenWarningCloudRequestReturnsHttpError() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));

            AiProvider.Result result = provider(properties).answer("我可以停药吗？");

            assertEquals("NORMAL", result.safetyLevel());
            assertEquals(1, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToNormalWhenWarningCloudResponseCannotBeParsed() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));

            AiProvider.Result result = provider(properties).answer("这是什么病？");

            assertEquals("NORMAL", result.safetyLevel());
            assertEquals(1, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotCallCloudForCriticalQuestion() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud", "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "qwen-plus", Duration.ofSeconds(2));

            AiProvider.Result result = provider(properties).answer("胸口很闷，呼吸困难");

            assertEquals("CRITICAL", result.safetyLevel());
            assertEquals(0, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    private CloudAiProvider provider(AiProviderProperties properties) {
        return new CloudAiProvider(properties, new AiSafetyClassifier(), new AiResponseGuard(), new ObjectMapper());
    }
}
