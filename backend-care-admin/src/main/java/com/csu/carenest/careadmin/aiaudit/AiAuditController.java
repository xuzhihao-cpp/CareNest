package com.csu.carenest.careadmin.aiaudit;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/sessions")
public class AiAuditController {
    private final AuthService auth;
    private final AiAuditService service;

    public AiAuditController(AuthService auth, AiAuditService service) {
        this.auth = auth;
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AiAuditDtos.PageResult<AiAuditDtos.SessionItem>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(name = "riskFlag", required = false) Boolean riskFlag,
            @RequestParam(name = "safetyLevel", required = false) String safetyLevel,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.list(user(authorization), riskFlag, safetyLevel, page, size));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<AiAuditDtos.SessionDetail> detail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("sessionId") String sessionId) {
        return ApiResponse.success(service.detail(user(authorization), sessionId));
    }

    private CurrentUser user(String authorization) {
        return auth.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
