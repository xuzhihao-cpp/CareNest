package com.csu.carenest.careadmin.metric;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 冻结阶段34-40路径、统一响应和请求校验。 */
@ExtendWith(MockitoExtension.class)
class Phase34To40MetricControllerTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_1", List.of(RoleCode.ADMIN));
    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));

    @Mock
    private AuthService authService;
    @Mock
    private Phase34To40MetricService metricService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Phase34To40MetricController(authService, metricService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void configPutUsesFrozenPathAndResponse() throws Exception {
        when(authService.requireAnyRole(
                eq("Bearer token"), eq(RoleCode.ADMIN), eq(RoleCode.CUSTOMER_SERVICE)))
                .thenReturn(ADMIN);
        when(metricService.saveMetricConfig(eq(ADMIN), eq("service_1"), any()))
                .thenReturn(new CareMetricDtos.ConfigVersionResponse(2, List.of()));

        mockMvc.perform(put("/api/v1/admin/service-items/service_1/care-metric-config")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"metricCode":"PHOTO","metricName":"服务照片",
                                "metricType":"SERVICE_PROCESS","required":true,
                                "evidenceType":"PHOTO","scoreWeight":10,"description":"留档"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.configVersion").value(2));
    }

    @Test
    void emptyConfigItemsAreRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(put("/api/v1/admin/service-items/service_1/care-metric-config")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void evidenceListReturnsOnlyFrozenRecordFields() throws Exception {
        when(authService.requireCurrentUser("Bearer token")).thenReturn(NURSE);
        when(metricService.evidences(NURSE, "order_1")).thenReturn(List.of(
                new CareMetricDtos.EvidenceResponse("evidence_1", "PENDING", null, null, null, null, null)));

        mockMvc.perform(get("/api/v1/orders/order_1/evidences")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].evidenceId").value("evidence_1"))
                .andExpect(jsonPath("$.data[0].auditStatus").value("PENDING"));
    }

    @Test
    void proofReviewReturnsStatusAndScoreDecision() throws Exception {
        when(authService.requireAnyRole(
                eq("Bearer token"), eq(RoleCode.ADMIN), eq(RoleCode.CUSTOMER_SERVICE)))
                .thenReturn(ADMIN);
        when(metricService.reviewExceptionProof(eq(ADMIN), eq("proof_1"), any()))
                .thenReturn(new CareMetricDtos.ProofReviewResponse(
                        "proof_1", "APPROVED", "NO_DEDUCTION"));

        mockMvc.perform(post("/api/v1/admin/metric-exception-proofs/proof_1/review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewResult":"APPROVED","reviewComment":"材料有效",
                                "scoreDecision":"NO_DEDUCTION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proofId").value("proof_1"))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.scoreDecision").value("NO_DEDUCTION"));
    }
}
