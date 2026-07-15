package com.csu.carenest.careadmin.attention;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 阶段31服务前注意事项接口入口。 */
@RestController
@RequestMapping("/api/v1")
public class Phase31AttentionController {

    private final AuthService authService;
    private final Phase31AttentionService attentionService;

    public Phase31AttentionController(AuthService authService, Phase31AttentionService attentionService) {
        this.authService = authService;
        this.attentionService = attentionService;
    }

    @GetMapping("/nurse/orders/{orderId}/attention-notices")
    public ApiResponse<AttentionNoticeDtos.AttentionNoticeResponse> attentionNotices(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireAnyRole(
                authorization, RoleCode.NURSE, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(attentionService.attentionNotices(currentUser, orderId));
    }

    @PostMapping("/nurse/orders/{orderId}/attention-notices/ack")
    public ApiResponse<AttentionNoticeDtos.AttentionNoticeResponse> acknowledge(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody AttentionNoticeDtos.AckRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(attentionService.acknowledge(currentUser, orderId, request));
    }
}
