package com.csu.carenest.user.auth;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PermissionApiTest.TestApplication.class)
@AutoConfigureMockMvc
class PermissionApiTest {

    private static final String DEMO_PASSWORD = "Demo@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void permissionsReturnCurrentUserButtonPermissions() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(get("/api/v1/auth/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.roleCode").value("FAMILY"))
                .andExpect(jsonPath("$.data.permissions[0]").value("FAMILY_ELDER_VIEW"))
                .andExpect(jsonPath("$.data.permissions[1]").value("FAMILY_ORDER_CREATE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void normalUserCannotUpdateAdminRolePermissions() throws Exception {
        String token = loginAndReadToken("family_demo");

        mockMvc.perform(post("/api/v1/admin/roles/NURSE/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("permissionCodes", List.of("NURSE_ORDER_VIEW")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void adminCanUpdateRolePermissionsAndCurrentRoleReadsUpdatedButtons() throws Exception {
        String adminToken = loginAndReadToken("admin_demo");

        mockMvc.perform(post("/api/v1/admin/roles/NURSE/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "permissionCodes",
                                List.of("NURSE_ORDER_VIEW", "NURSE_REPORT_CREATE", "NURSE_APPEAL_CREATE")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("NURSE"))
                .andExpect(jsonPath("$.data.permissions[2]").value("NURSE_APPEAL_CREATE"));

        String nurseToken = loginAndReadToken("nurse_demo");
        mockMvc.perform(get("/api/v1/auth/permissions")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("NURSE"))
                .andExpect(jsonPath("$.data.permissions[2]").value("NURSE_APPEAL_CREATE"));
    }

    @Test
    void missingBearerTokenCannotReadPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/auth/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录"));
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
