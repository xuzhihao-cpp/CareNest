package com.csu.carenest.careadmin.attention;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 阶段31冻结路径、统一响应和参数校验测试。 */
@ExtendWith(MockitoExtension.class)
class Phase31AttentionControllerTest {

    private static final CurrentUser NURSE = new CurrentUser("nurse_1", List.of(RoleCode.NURSE));

    @Mock
    private AuthService authService;
    @Mock
    private Phase31AttentionService attentionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Phase31AttentionController(authService, attentionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUsesFrozenPathAndResponseFields() throws Exception {
        when(authService.requireAnyRole(
                eq("Bearer token"), eq(RoleCode.NURSE), eq(RoleCode.ADMIN), eq(RoleCode.CUSTOMER_SERVICE)))
                .thenReturn(NURSE);
        when(attentionService.attentionNotices(NURSE, "order_1"))
                .thenReturn(new AttentionNoticeDtos.AttentionNoticeResponse(List.of(
                        new AttentionNoticeDtos.AttentionNoticeItem(
                                "notice_1", "CRITICAL", "核对过敏史", "HEALTH_ARCHIVE",
                                true, false, null))));

        mockMvc.perform(get("/api/v1/nurse/orders/order_1/attention-notices")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].noticeId").value("notice_1"))
                .andExpect(jsonPath("$.data.items[0].requiredAck").value(true))
                .andExpect(jsonPath("$.data.items[0].acknowledged").value(false));
    }

    @Test
    void ackRejectsEmptyNoticeIds() throws Exception {
        mockMvc.perform(post("/api/v1/nurse/orders/order_1/attention-notices/ack")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noticeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void ackReturnsLatestReadModel() throws Exception {
        when(authService.requireRole("Bearer token", RoleCode.NURSE)).thenReturn(NURSE);
        when(attentionService.acknowledge(eq(NURSE), eq("order_1"), any()))
                .thenReturn(new AttentionNoticeDtos.AttentionNoticeResponse(List.of(
                        new AttentionNoticeDtos.AttentionNoticeItem(
                                "notice_1", "WARNING", "重点观察", "HEALTH_ARCHIVE",
                                true, true, "2026-07-15T17:00:00"))));

        mockMvc.perform(post("/api/v1/nurse/orders/order_1/attention-notices/ack")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noticeIds\":[\"notice_1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].acknowledged").value(true))
                .andExpect(jsonPath("$.data.items[0].acknowledgedAt").value("2026-07-15T17:00:00"));
    }
}
