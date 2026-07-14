package com.csu.carenest.user.healthfeedback;

import com.csu.carenest.user.medicalfile.MedicalFileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase22HealthFeedbackApiTest.TestApplication.class)
@AutoConfigureMockMvc
@Transactional
class Phase22HealthFeedbackApiTest {
    private static final String PASSWORD = "Demo@123456";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean MedicalFileStorage storage;

    @BeforeEach
    void configureStorage() {
        when(storage.presignedGet(any(String.class), any(Duration.class)))
                .thenReturn("https://media.example.test/voice.mp3");
    }

    @Test
    void elderCreatesFeedbackForSelfAndFamilyReadsHighSeverityFirst() throws Exception {
        String elder = login("elder_demo");
        create(elder, "SLEEP", "LOW", "TEXT", "昨晚睡得不好", null).andExpect(status().isOk());
        create(elder, "PAIN", "HIGH", "BUTTON", "", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedbackId").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        String family = login("family_demo");
        mockMvc.perform(get("/api/v1/family/elders/elder_001/health-feedback")
                        .header("Authorization", bearer(family))
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].elderId").value("elder_001"))
                .andExpect(jsonPath("$.data.records[0].elderName").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].severity").value("HIGH"));

        assertEquals("elder_001", jdbc.queryForObject(
                "SELECT elder_id FROM elder_health_feedback WHERE severity='HIGH'", String.class));
    }

    @Test
    void createRejectsNonElderAndDoesNotAcceptCallerSelectedElder() throws Exception {
        String family = login("family_demo");
        create(family, "PAIN", "LOW", "BUTTON", "", null)
                .andExpect(status().isForbidden());

        String elder = login("elder_demo");
        String body = objectMapper.writeValueAsString(Map.of(
                "elderId", "elder_002", "feedbackType", "PAIN", "severity", "LOW",
                "content", "", "inputType", "BUTTON"));
        mockMvc.perform(post("/api/v1/elder/health-feedback")
                        .header("Authorization", bearer(elder))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void familyReadRequiresActiveHealthViewBinding() throws Exception {
        String family = login("family_demo");
        mockMvc.perform(get("/api/v1/family/elders/elder_002/health-feedback")
                        .header("Authorization", bearer(family)))
                .andExpect(status().isForbidden());

        String nurse = login("nurse_demo");
        mockMvc.perform(get("/api/v1/family/elders/elder_001/health-feedback")
                        .header("Authorization", bearer(nurse)))
                .andExpect(status().isForbidden());
    }

    @Test
    void voiceFeedbackRequiresOwnedAudioAndWritesVoiceLog() throws Exception {
        String elder = login("elder_demo");
        String fileId = uploadMp3(elder);
        create(elder, "DIZZINESS", "MEDIUM", "VOICE", "起身时头晕", fileId)
                .andExpect(status().isOk());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM voice_command_log WHERE user_id='elder-001' AND file_id=?",
                Integer.class, fileId));

        String family = login("family_demo");
        mockMvc.perform(get("/api/v1/family/elders/elder_001/health-feedback")
                        .header("Authorization", bearer(family)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].inputType").value("VOICE"))
                .andExpect(jsonPath("$.data.records[0].voiceUrl").value("https://media.example.test/voice.mp3"));
    }

    @Test
    void rejectsForeignAudioAndAudioAsMedicalFile() throws Exception {
        String family = login("family_demo");
        String foreignFile = uploadMp3(family);
        String elder = login("elder_demo");
        create(elder, "PAIN", "LOW", "VOICE", "", foreignFile)
                .andExpect(status().isForbidden());

        String registerBody = objectMapper.writeValueAsString(Map.of(
                "fileId", foreignFile, "fileType", "MEDICAL_RECORD",
                "title", "语音", "occurredAt", "2026-07-01"));
        mockMvc.perform(post("/api/v1/elders/elder_001/medical-files")
                        .header("Authorization", bearer(family))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void highSeverityOnlyCreatesAuditSignalAndDoesNotMutateArchive() throws Exception {
        String before = jdbc.queryForObject(
                "SELECT care_summary FROM health_archive WHERE elder_id='elder_001'", String.class);
        create(login("elder_demo"), "MENTAL_STATE", "HIGH", "TEXT", "今天情绪很差", null)
                .andExpect(status().isOk());
        assertEquals(before, jdbc.queryForObject(
                "SELECT care_summary FROM health_archive WHERE elder_id='elder_001'", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type='HIGH_HEALTH_FEEDBACK'",
                Integer.class));
    }

    private org.springframework.test.web.servlet.ResultActions create(
            String token, String type, String severity, String input, String content, String fileId) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("feedbackType", type);
        payload.put("severity", severity);
        payload.put("content", content);
        payload.put("inputType", input);
        payload.put("fileId", fileId);
        return mockMvc.perform(post("/api/v1/elder/health-feedback")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private String uploadMp3(String token) throws Exception {
        byte[] mp3 = "ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000audio".getBytes(StandardCharsets.ISO_8859_1);
        String response = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "voice.mp3", "audio/mpeg", mp3))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("fileId").asText();
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {}
}
