package com.csu.carenest.careadmin.healthsuggestion;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.common.ApiResponse;
import com.csu.carenest.careadmin.common.PageData;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HealthSuggestionController {
    private final AuthService auth;
    private final HealthSuggestionService service;
    public HealthSuggestionController(AuthService auth, HealthSuggestionService service) { this.auth = auth; this.service = service; }

    @PostMapping("/orders/{orderId}/health-update-suggestions")
    public ApiResponse<HealthSuggestionDtos.CreateResult> create(
            @RequestHeader("Authorization") String authorization, @PathVariable("orderId") String orderId,
            @RequestBody HealthSuggestionDtos.CreateRequest request) {
        return ApiResponse.success(service.create(auth.requireCurrentUser(authorization), orderId, request));
    }

    @GetMapping("/admin/health-review-tasks")
    public ApiResponse<PageData<HealthSuggestionDtos.ReviewTaskItem>> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(name="page", defaultValue="1") int page,
            @RequestParam(name="size", defaultValue="20") int size,
            @RequestParam(name="status", required=false) String status,
            @RequestParam(name="sourceType", required=false) String sourceType,
            @RequestParam(name="keyword", required=false) String keyword) {
        return ApiResponse.success(service.list(auth.requireCurrentUser(authorization), page, size, status, sourceType, keyword));
    }
}
