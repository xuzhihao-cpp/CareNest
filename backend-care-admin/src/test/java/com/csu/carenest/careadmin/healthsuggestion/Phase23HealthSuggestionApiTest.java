package com.csu.carenest.careadmin.healthsuggestion;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase23HealthSuggestionApiTest.App.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:phase23;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=never", "spring.data.redis.repositories.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = {"classpath:phase23-schema.sql", "classpath:phase23-data.sql"},
        config = @SqlConfig(encoding = "UTF-8"))
@Transactional
class Phase23HealthSuggestionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean AuthService auth;

    @Test
    void assignedNurseCreatesPendingSuggestionWithoutChangingArchiveAndAdminReadsIt() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        String body = request("diseases", Map.of("diseaseName", "糖尿病", "status", "MONITORING"), "SERVICE_RECORD", "record_001");
        mvc.perform(post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "nurse")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        assertEquals(1, count("health_update_suggestion"));
        assertEquals(1, count("health_info_review_task"));
        assertEquals(1, count("operation_log"));
        assertEquals(1, jdbc.queryForObject("SELECT archive_version FROM health_archive", Integer.class));

        user("admin", "admin-001", RoleCode.ADMIN);
        mvc.perform(get("/api/v1/admin/health-review-tasks").header("Authorization", "admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].elderName").value("张爷爷"))
                .andExpect(jsonPath("$.data.records[0].serviceName").value("上门护理"))
                .andExpect(jsonPath("$.data.records[0].fieldName").value("diseases"))
                .andExpect(jsonPath("$.data.records[0].currentValue[0].diseaseName").value("高血压"));
    }

    @Test
    void duplicatePendingSuggestionReturnsConflictWithoutExtraRows() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        String body = request("riskTags", Map.of("tagCode", "FALL_RISK", "tagName", "跌倒风险"), "SERVICE_REPORT", "report_001");
        var call = post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "nurse")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        mvc.perform(call).andExpect(status().isOk());
        mvc.perform(post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "nurse")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertEquals(1, count("health_update_suggestion"));
        assertEquals(1, count("health_info_review_task"));
    }

    @Test
    void rejectsWrongNurseForgedSourceAndInvalidStructuredValue() throws Exception {
        user("other", "nurse-002", RoleCode.NURSE);
        mvc.perform(post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "other")
                        .contentType(MediaType.APPLICATION_JSON).content(request("diseases", Map.of("diseaseName", "糖尿病", "status", "ACTIVE"), "SERVICE_RECORD", "record_001")))
                .andExpect(status().isForbidden());
        user("nurse", "nurse-001", RoleCode.NURSE);
        mvc.perform(post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "nurse")
                        .contentType(MediaType.APPLICATION_JSON).content(request("diseases", Map.of("diseaseName", "糖尿病", "status", "ACTIVE"), "SERVICE_RECORD", "missing")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/orders/order_001/health-update-suggestions").header("Authorization", "nurse")
                        .contentType(MediaType.APPLICATION_JSON).content(request("medications", Map.of("medicationName", "药物", "frequency", "INVALID"), "SERVICE_RECORD", "record_001")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminListRequiresRoleAndEnabledPermission() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        mvc.perform(get("/api/v1/admin/health-review-tasks").header("Authorization", "nurse"))
                .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_permission WHERE role_id='role_admin'");
        user("admin", "admin-001", RoleCode.ADMIN);
        mvc.perform(get("/api/v1/admin/health-review-tasks").header("Authorization", "admin"))
                .andExpect(status().isForbidden());
    }

    private String request(String field, Object value, String sourceType, String sourceId) throws Exception {
        return json.writeValueAsString(Map.of("fieldName", field, "newValue", value, "sourceType", sourceType,
                "sourceId", sourceId, "reason", "根据本次护理服务建议更新"));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void user(String token, String id, RoleCode role) {
        when(auth.requireCurrentUser(token)).thenReturn(new CurrentUser(id, List.of(role)));
    }

    @SpringBootConfiguration @EnableAutoConfiguration @ComponentScan("com.csu.carenest.careadmin")
    static class App {}
}
