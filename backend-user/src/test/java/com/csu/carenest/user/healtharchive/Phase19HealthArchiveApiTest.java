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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
