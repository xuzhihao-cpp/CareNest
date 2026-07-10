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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UserSidePhaseApiTest.TestApplication.class)
@AutoConfigureMockMvc
class UserSidePhaseApiTest {

    private static final String DEMO_PASSWORD = "Demo@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void familyCreatesElderBindingAndElderApprovesThenFamilyUpdatesScopesAndRevokes() throws Exception {
        String familyToken = loginAndReadToken("family_demo");
        String elderToken = loginAndReadToken("elder_demo");

        String createBody = mockMvc.perform(post("/api/v1/family/bindings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.elderId").value("elder_001"))
                .andExpect(jsonPath("$.data.elderName").value("张爷爷"))
                .andExpect(jsonPath("$.data.relationType").value("DAUGHTER"))
                .andExpect(jsonPath("$.data.bindingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.scopeCodes[0]").value("HEALTH_VIEW"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String bindingId = objectMapper.readTree(createBody).path("data").path("bindingId").asText();

        mockMvc.perform(post("/api/v1/elder/bindings/{bindingId}/approve", bindingId)
                        .header("Authorization", "Bearer " + elderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bindingStatus").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/family/bindings/{bindingId}/scopes", bindingId)
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW", "HEALTH_EDIT", "REPORT_CONFIRM")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bindingStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.scopeCodes", hasItem("HEALTH_EDIT")));

        mockMvc.perform(post("/api/v1/family/bindings/{bindingId}/revoke", bindingId)
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bindingStatus").value("REVOKED"));
    }

    @Test
    void familyReadsAndUpdatesAuthorizedElderProfile() throws Exception {
        String familyToken = loginAndReadToken("family_demo");

        mockMvc.perform(get("/api/v1/family/elders")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].elderId").value("elder_001"))
                .andExpect(jsonPath("$.data[0].profileVersion").isNotEmpty());

        String beforeBody = mockMvc.perform(get("/api/v1/elders/elder_001/profile")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elderId").value("elder_001"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String beforeVersion = objectMapper.readTree(beforeBody).path("data").path("profileVersion").asText();

        mockMvc.perform(put("/api/v1/elders/elder_001/profile")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "张爷爷",
                                "gender", "MALE",
                                "birthDate", "1946-05-12",
                                "careLevel", "LEVEL_3",
                                "emergencyContacts", List.of(Map.of(
                                        "contactName", "张小明",
                                        "contactPhone", "13800000002",
                                        "relationType", "SON"
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.elderId").value("elder_001"))
                .andExpect(jsonPath("$.data.profileVersion").value(not(beforeVersion)));
    }

    @Test
    void familyManagesAuthorizedServiceAddressesAndDefaultAddress() throws Exception {
        String familyToken = loginAndReadToken("family_demo");

        mockMvc.perform(get("/api/v1/elders/elder_001/service-addresses")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].addressId").value("address_001"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));

        String createBody = mockMvc.perform(post("/api/v1/elders/elder_001/service-addresses")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactName", "张小明",
                                "contactPhone", "13800000002",
                                "regionCode", "310101",
                                "detailAddress", "人民路200号2单元301",
                                "isDefault", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullAddress").value("310000310100310101人民路200号2单元301"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String addressId = objectMapper.readTree(createBody).path("data").path("addressId").asText();

        mockMvc.perform(put("/api/v1/service-addresses/{addressId}", addressId)
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactName", "张小明",
                                "contactPhone", "13800000002",
                                "regionCode", "310101",
                                "detailAddress", "人民路300号",
                                "isDefault", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressId").value(addressId))
                .andExpect(jsonPath("$.data.isDefault").value(false));

        mockMvc.perform(delete("/api/v1/service-addresses/{addressId}", addressId)
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressId").value(addressId));
    }

    @Test
    void nurseCannotAccessFamilyBindingApis() throws Exception {
        String nurseToken = loginAndReadToken("nurse_demo");

        mockMvc.perform(get("/api/v1/family/bindings")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限"));
    }

    @Test
    void familyCannotApproveBindingThroughElderEndpoint() throws Exception {
        String familyToken = loginAndReadToken("family_demo");

        String createBody = mockMvc.perform(post("/api/v1/family/bindings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bindingStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String bindingId = objectMapper.readTree(createBody).path("data").path("bindingId").asText();

        mockMvc.perform(post("/api/v1/elder/bindings/{bindingId}/approve", bindingId)
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "elderInviteCode", "elder_001",
                                "relationType", "DAUGHTER",
                                "scopeCodes", List.of("HEALTH_VIEW")
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void phase18ReportsHealthAndDemoDataStatus() throws Exception {
        String adminToken = loginAndReadToken("admin_demo");

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.appName").value("CareNest"))
                .andExpect(jsonPath("$.data.version").value("0.1.0"))
                .andExpect(jsonPath("$.data.dbConnected").value(true))
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/demo-data/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.accounts", hasItems(
                        "elder_demo", "family_demo", "nurse_demo", "admin_demo", "cs_demo"
                )))
                .andExpect(jsonPath("$.data.scenarioCount").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void nurseCannotReadDemoDataStatus() throws Exception {
        String nurseToken = loginAndReadToken("nurse_demo");

        mockMvc.perform(get("/api/v1/admin/demo-data/status")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private String loginAndReadToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.user")
    static class TestApplication {
    }
}
