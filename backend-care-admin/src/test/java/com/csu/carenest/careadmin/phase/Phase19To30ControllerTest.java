package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.GlobalExceptionHandler;
import com.csu.carenest.careadmin.phase.dto.MedicalFileDtos;
import com.csu.carenest.careadmin.phase.dto.RecommendationDtos;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段21至30接口路径、统一响应和参数校验测试。
 */
@ExtendWith(MockitoExtension.class)
class Phase19To30ControllerTest {

    private static final CurrentUser ADMIN = new CurrentUser("admin_demo", List.of(RoleCode.ADMIN));
    private static final CurrentUser FAMILY = new CurrentUser("family_demo", List.of(RoleCode.FAMILY));

    @Mock
    private AuthService authService;

    @Mock
    private Phase19To30Service phaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Phase19To30Controller(authService, phaseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void medicalFileReviewUsesFrozenPathAndResponseFields() throws Exception {
        when(authService.requireAnyRole(eq("Bearer token"), eq(RoleCode.ADMIN), eq(RoleCode.CUSTOMER_SERVICE)))
                .thenReturn(ADMIN);
        when(phaseService.reviewMedicalFile(eq(ADMIN), eq("medical_1"), any()))
                .thenReturn(new MedicalFileDtos.ReviewResponse(
                        "medical_1", "APPROVED", "2026-07-11T09:00:00"));

        mockMvc.perform(post("/api/v1/admin/medical-files/medical_1/review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "auditStatus":"APPROVED",
                                  "reviewComment":"资料清晰",
                                  "extractToArchive":false,
                                  "extractedItems":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value("medical_1"))
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"));
    }

    @Test
    void qualificationRequestRejectsMissingLockedFields() throws Exception {
        mockMvc.perform(post("/api/v1/nurse/qualification-applications")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"护理甲\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void preferredNurseEndpointDoesNotExposeDispatchResponse() throws Exception {
        when(authService.requireRole("Bearer token", RoleCode.FAMILY)).thenReturn(FAMILY);
        when(phaseService.choosePreferredNurse(eq(FAMILY), eq("order_1"), any()))
                .thenReturn(new RecommendationDtos.PreferredNurseResponse(
                        "order_1", "nurse_demo", "综合评分推荐"));

        mockMvc.perform(put("/api/v1/family/orders/order_1/preferred-nurse")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredNurseId\":\"nurse_demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order_1"))
                .andExpect(jsonPath("$.data.preferredNurseId").value("nurse_demo"))
                .andExpect(jsonPath("$.data.recommendReason").value("综合评分推荐"))
                .andExpect(jsonPath("$.data.taskId").doesNotExist());
    }
}
