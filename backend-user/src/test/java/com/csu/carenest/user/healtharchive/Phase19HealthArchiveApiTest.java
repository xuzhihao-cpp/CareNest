package com.csu.carenest.user.healtharchive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase19HealthArchiveApiTest.TestApplication.class)
@AutoConfigureMockMvc
@Transactional
class Phase19HealthArchiveApiTest {

    private static final String DEMO_PASSWORD = "Demo@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void elderReadsOwnHealthArchiveUsingFrontendContract() throws Exception {
        String token = loginAndReadToken("elder_demo");

        mockMvc.perform(get("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.elderId").value("elder_001"))
                .andExpect(jsonPath("$.data.archiveVersion").value(1))
                .andExpect(jsonPath("$.data.diseases[0].diseaseName").value("高血压"))
                .andExpect(jsonPath("$.data.medications[0].frequency").value("ONCE_DAILY"))
                .andExpect(jsonPath("$.data.medications[0].timePoints[0]").value("08:00"))
                .andExpect(jsonPath("$.data.allergies[0].allergenName").value("青霉素"))
                .andExpect(jsonPath("$.data.riskTags[0].tagCode").value("FALL_RISK"))
                .andExpect(jsonPath("$.data.carePlan.careGoals").value("保持血压稳定"));
    }

    @Test
    void activeFamilyWithHealthViewReadsArchive() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(get("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void elderCannotReadAnotherEldersArchive() throws Exception {
        String token = loginAndReadToken("elder_other_demo");

        mockMvc.perform(get("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void pendingFamilyBindingCannotReadArchive() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(get("/api/v1/elders/elder_002/health-archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void familyUpdatesWholeArchiveInOneVersionedTransaction() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(put("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validArchiveUpdate(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.archiveVersion").value(2));

        mockMvc.perform(get("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archiveVersion").value(2))
                .andExpect(jsonPath("$.data.diseases[0].diseaseName").value("糖尿病"))
                .andExpect(jsonPath("$.data.medications[0].medicationName").value("二甲双胍"))
                .andExpect(jsonPath("$.data.allergies[0].allergenName").value("花生"))
                .andExpect(jsonPath("$.data.riskTags[0].tagCode").value("MEDICATION_RISK"))
                .andExpect(jsonPath("$.data.carePlan.dailyCare").value("记录血糖"));

        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM health_archive_change_log WHERE elder_id = 'elder_001' AND change_type = 'FAMILY_EDIT'"));
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM operation_log WHERE biz_id = 'elder_001' AND operation_type = 'UPDATE_HEALTH_ARCHIVE'"));
    }

    @Test
    void staleArchiveVersionReturnsConflictWithoutChangingDataOrLogs() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(put("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validArchiveUpdate(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT archive_version FROM health_archive WHERE elder_id = 'elder_001'"));
        org.junit.jupiter.api.Assertions.assertEquals("高血压", jdbcTemplate.queryForObject(
                "SELECT disease_name FROM chronic_disease WHERE elder_id = 'elder_001'", String.class));
        org.junit.jupiter.api.Assertions.assertEquals(0, count(
                "SELECT COUNT(*) FROM operation_log WHERE biz_id = 'elder_001' AND operation_type = 'UPDATE_HEALTH_ARCHIVE'"));
    }

    @Test
    void elderCannotUpdateFamilyMaintainedArchive() throws Exception {
        String token = loginAndReadToken("elder_demo");

        mockMvc.perform(put("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validArchiveUpdate(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void familyWithoutHealthEditCannotUpdateArchive() throws Exception {
        jdbcTemplate.update(
                "UPDATE elder_family_binding SET scope_codes = '[\"HEALTH_VIEW\"]' WHERE binding_id = 'binding_001'");
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(put("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validArchiveUpdate(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void duplicateMedicationNamesReturnUnprocessableEntity() throws Exception {
        String token = loginAndReadToken("family_demo");
        Map<String, Object> payload = validArchiveUpdate(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> medications = (List<Map<String, Object>>) payload.get("medications");
        payload.put("medications", List.of(medications.get(0), new LinkedHashMap<>(medications.get(0))));

        mockMvc.perform(put("/api/v1/elders/elder_001/health-archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void familyQuickAddsMedicationWithVersionCheck() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/elders/elder_001/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMedicationCreate(1, "阿司匹林"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.archiveVersion").value(2))
                .andExpect(jsonPath("$.data.medication.medicationName").value("阿司匹林"));

        org.junit.jupiter.api.Assertions.assertEquals(2, count(
                "SELECT COUNT(*) FROM medication_plan WHERE elder_id = 'elder_001'"));
        org.junit.jupiter.api.Assertions.assertEquals(1, count(
                "SELECT COUNT(*) FROM operation_log WHERE biz_id = 'elder_001' AND operation_type = 'ADD_HEALTH_ARCHIVE_MEDICATION'"));
    }

    @Test
    void quickAddRejectsExistingMedicationName() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/elders/elder_001/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMedicationCreate(1, " 降压药 "))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void quickAddRejectsStaleArchiveVersion() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/elders/elder_001/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMedicationCreate(2, "阿司匹林"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void quickAddRejectsInvalidMedicationTime() throws Exception {
        String token = loginAndReadToken("family_demo");
        Map<String, Object> payload = validMedicationCreate(1, "阿司匹林");
        payload.put("timePoints", List.of("25:00"));

        mockMvc.perform(post("/api/v1/elders/elder_001/medications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private Map<String, Object> validMedicationCreate(int archiveVersion, String medicationName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("archiveVersion", archiveVersion);
        payload.put("medicationName", medicationName);
        payload.put("dosage", "1片");
        payload.put("frequency", "ONCE_DAILY");
        payload.put("timePoints", List.of("08:00"));
        payload.put("startDate", "2025-01-01");
        payload.put("endDate", "");
        payload.put("remark", "仅作记录");
        return payload;
    }

    private Map<String, Object> validArchiveUpdate(int archiveVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("archiveVersion", archiveVersion);
        payload.put("diseases", List.of(Map.of(
                "diseaseName", "糖尿病",
                "diagnosedAt", "2023-04-10",
                "status", "MONITORING",
                "remark", "关注空腹血糖")));
        payload.put("medications", List.of(Map.of(
                "medicationName", "二甲双胍",
                "dosage", "1片",
                "frequency", "TWICE_DAILY",
                "timePoints", List.of("08:00", "18:00"),
                "startDate", "2024-01-01",
                "endDate", "",
                "remark", "仅作记录")));
        payload.put("allergies", List.of(Map.of(
                "allergenName", "花生",
                "reaction", "皮疹",
                "severity", "MODERATE",
                "remark", "避免接触")));
        payload.put("riskTags", List.of("MEDICATION_RISK"));
        payload.put("carePlan", Map.of(
                "careGoals", "稳定血糖",
                "dailyCare", "记录血糖",
                "precautions", "留意低血糖表现"));
        return payload;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String loginAndReadToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", DEMO_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.path("data").path("token").asText();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }
}
