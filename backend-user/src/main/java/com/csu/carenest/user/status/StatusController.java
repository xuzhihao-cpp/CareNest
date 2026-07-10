package com.csu.carenest.user.status;

import com.csu.carenest.user.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/v1/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(statusService.health());
    }

    @GetMapping("/api/v1/admin/demo-data/status")
    public ApiResponse<DemoDataStatusResponse> demoDataStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(statusService.demoDataStatus(authorization));
    }
}
