package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Phase25PreServiceSummaryApiTest.App.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:phase25;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=never", "spring.data.redis.repositories.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = {"classpath:phase25-schema.sql", "classpath:phase25-data.sql"},
        config = @SqlConfig(encoding = "UTF-8"))
@Transactional
class Phase25PreServiceSummaryApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockBean AuthService auth;
    @MockBean Phase25MedicalFileStorage storage;

    @Test
    void assignedNurseReadsOnlySafeReviewedDataAndWritesAccessLog() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);

        mvc.perform(get("/api/v1/nurse/orders/order_001/pre-service-health-summary")
                        .header("Authorization", "nurse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elderProfile.elderName").value("张爷爷"))
                .andExpect(jsonPath("$.data.elderProfile.carePlan.precautions").value("起身缓慢"))
                .andExpect(jsonPath("$.data.elderProfile.elderId").doesNotExist())
                .andExpect(jsonPath("$.data.elderProfile.archiveVersion").doesNotExist())
                .andExpect(jsonPath("$.data.riskTags[0].tagCode").value("FALL_RISK"))
                .andExpect(jsonPath("$.data.medications[0].timePoints[0]").value("08:00"))
                .andExpect(jsonPath("$.data.medications[0].medicationId").doesNotExist())
                .andExpect(jsonPath("$.data.medications[0].endDate").doesNotExist())
                .andExpect(jsonPath("$.data.diseases[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.allergies[0].allergenName").value("青霉素"))
                .andExpect(jsonPath("$.data.approvedMedicalFiles.length()").value(1))
                .andExpect(jsonPath("$.data.approvedMedicalFiles[0].auditStatus").doesNotExist())
                .andExpect(jsonPath("$.data.approvedMedicalFiles[0].fileId").doesNotExist())
                .andExpect(jsonPath("$.data.approvedMedicalFiles[0].summary").doesNotExist())
                .andExpect(jsonPath("$.data.recentReports.length()").value(1))
                .andExpect(jsonPath("$.data.recentReports[0].serviceName").value("基础上门护理"))
                .andExpect(jsonPath("$.data.recentReports[0].reportId").doesNotExist())
                .andExpect(jsonPath("$.data.recentReports[0].vitalSigns[0]").value("130/80 mmHg"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("OTHER_ELDER_SECRET"))));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type='VIEW_PRE_SERVICE_HEALTH_SUMMARY'",
                Integer.class));
    }

    @Test
    void unassignedNurseAndUserRolesAreForbidden() throws Exception {
        user("other-nurse", "nurse-002", RoleCode.NURSE);
        mvc.perform(get("/api/v1/nurse/orders/order_001/pre-service-health-summary")
                        .header("Authorization", "other-nurse"))
                .andExpect(status().isForbidden());

        when(auth.requireAnyRole(any(), any(RoleCode[].class))).thenThrow(new ForbiddenException());
        mvc.perform(get("/api/v1/nurse/orders/order_001/pre-service-health-summary")
                        .header("Authorization", "family"))
                .andExpect(status().isForbidden());
    }

    @Test
    void servingTaskIsOutsideFrozenPreServiceWindow() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        mvc.perform(get("/api/v1/nurse/orders/order_serving/pre-service-health-summary")
                        .header("Authorization", "nurse"))
                .andExpect(status().isConflict());
    }

    @Test
    void assignedNurseCanPreviewOnlyApprovedFileThroughProtectedRoute() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        when(storage.read("smart-nursing", "medical/approved.pdf"))
                .thenReturn("%PDF-preview".getBytes(StandardCharsets.US_ASCII));

        mvc.perform(get("/api/v1/nurse/orders/order_001/medical-files/medical_approved/preview")
                        .header("Authorization", "nurse"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "application/pdf"));

        mvc.perform(get("/api/v1/nurse/orders/order_001/medical-files/medical_pending/preview")
                        .header("Authorization", "nurse"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveRefreshReadsNewVersionSnapshotAndAdminCanReviewAnyTaskState() throws Exception {
        user("nurse", "nurse-001", RoleCode.NURSE);
        mvc.perform(get("/api/v1/nurse/orders/order_001/pre-service-health-summary")
                        .header("Authorization", "nurse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskTags.length()").value(1));

        jdbc.update("UPDATE health_archive SET archive_version=4 WHERE elder_id='elder_001'");
        jdbc.update("INSERT INTO risk_tag VALUES (?,?,?,?,?,?)",
                "risk_002", "elder_001", "PRESSURE_SORE_RISK", "压疮风险", "HIGH", null);

        mvc.perform(get("/api/v1/nurse/orders/order_001/pre-service-health-summary")
                        .header("Authorization", "nurse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskTags.length()").value(2));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM operation_log
                WHERE operation_type='VIEW_PRE_SERVICE_HEALTH_SUMMARY'
                  AND after_value LIKE '%\"archiveVersion\":4%'
                """, Integer.class));

        user("admin", "admin-001", RoleCode.ADMIN);
        mvc.perform(get("/api/v1/nurse/orders/order_serving/pre-service-health-summary")
                        .header("Authorization", "admin"))
                .andExpect(status().isOk());
    }

    private void user(String token, String userId, RoleCode role) {
        CurrentUser current = new CurrentUser(userId, List.of(role));
        when(auth.requireAnyRole(token, RoleCode.NURSE, RoleCode.ADMIN)).thenReturn(current);
        when(auth.requireCurrentUser(token)).thenReturn(current);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("com.csu.carenest.careadmin")
    static class App {}
}
