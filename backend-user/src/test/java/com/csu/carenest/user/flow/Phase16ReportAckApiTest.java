package com.csu.carenest.user.flow;

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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase16ReportAckApiTest.TestApplication.class)
@AutoConfigureMockMvc
@Sql(scripts = "/phase16-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class Phase16ReportAckApiTest {

    private static final String DEMO_PASSWORD = "Demo@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void elderAcceptsReportAndCompletesOrder() throws Exception {
        String token = loginAndReadToken("elder_demo");

        mockMvc.perform(post("/api/v1/elder/reports/report_ack_elder/ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson("ACCEPTED", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ackResult").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.reportStatus").value("CONFIRMED"));

        assertEquals("CONFIRMED", scalar("SELECT report_status FROM service_report WHERE report_id = 'report_ack_elder'"));
        assertEquals("COMPLETED", scalar("SELECT order_status FROM nursing_order WHERE order_id = 'order_ack_elder'"));
    }

    @Test
    void authorizedFamilyRejectsReportAndReturnsOrderForReportHandling() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/family/reports/report_ack_family/ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson("REJECTED", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ackResult").value("REJECTED"))
                .andExpect(jsonPath("$.data.reportStatus").value("REJECTED"));

        assertEquals("WAIT_REPORT", scalar("SELECT order_status FROM nursing_order WHERE order_id = 'order_ack_family'"));
    }

    @Test
    void familyDecidesArchiveSuggestions() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/family/reports/report_archive/archive-suggestions/decision")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson("ACCEPTED", List.of("review_accept"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ackResult").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.reportStatus").value("CONFIRMED"));

        assertEquals("APPROVED", scalar("SELECT review_status FROM health_info_review_task WHERE review_task_id = 'review_accept'"));
        assertEquals("REJECTED", scalar("SELECT review_status FROM health_info_review_task WHERE review_task_id = 'review_reject'"));
    }

    @Test
    void familyWithoutReportConfirmScopeIsForbidden() throws Exception {
        String token = loginAndReadToken("family_no_scope_demo");

        mockMvc.perform(post("/api/v1/family/reports/report_forbidden/ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson("ACCEPTED", List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void familyCannotUseElderAcknowledgementEndpoint() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/elder/reports/report_ack_elder/ack")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson("ACCEPTED", List.of())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String ackJson(String ackResult, List<String> acceptedSuggestionIds) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "ackResult", ackResult,
                "satisfaction", 5,
                "remark", "phase 16 test",
                "acceptedSuggestionIds", acceptedSuggestionIds
        ));
    }

    private String scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private String loginAndReadToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", DEMO_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }
}
