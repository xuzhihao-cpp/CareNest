package com.csu.carenest.user.ai;

import com.csu.carenest.user.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSpeechTranscriptionProviderTest {
    @Test
    void sendsBase64AudioToOpenAiCompatibleAsrEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"model":"qwen3-asr-flash","choices":[{"message":{"content":"请提醒我下午三点测量血压"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key",
                    "qwen-plus",
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "asr-key",
                    "qwen3-asr-flash",
                    Duration.ofSeconds(2)
            );
            AiSpeechTranscriptionProvider provider = new AiSpeechTranscriptionProvider(properties, new ObjectMapper());

            AiSpeechTranscriptionProvider.Result result =
                    provider.transcribe("audio-bytes".getBytes(StandardCharsets.UTF_8), "audio/wav", "question.wav");

            assertEquals("请提醒我下午三点测量血压", result.transcript());
            assertEquals("qwen3-asr-flash", result.model());
            assertTrue(requestBody.get().contains("\"model\":\"qwen3-asr-flash\""));
            assertTrue(requestBody.get().contains("\"type\":\"input_audio\""));
            assertTrue(requestBody.get().contains("data:audio/wav;base64,YXVkaW8tYnl0ZXM="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void convertsEmptyProviderTranscriptToApiError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderProperties properties = new AiProviderProperties(
                    "cloud",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key",
                    "qwen-plus",
                    Duration.ofSeconds(2),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "asr-key",
                    "qwen3-asr-flash",
                    Duration.ofSeconds(2)
            );
            AiSpeechTranscriptionProvider provider = new AiSpeechTranscriptionProvider(properties, new ObjectMapper());

            ApiException error = assertThrows(ApiException.class,
                    () -> provider.transcribe(new byte[]{1, 2}, "audio/wav", "empty.wav"));

            assertEquals(502, error.code());
        } finally {
            server.stop(0);
        }
    }
}
