package com.csu.carenest.careadmin.status;

import com.csu.carenest.careadmin.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CareAdminStatusController {

    private final CareAdminStatusService statusService;

    public CareAdminStatusController(CareAdminStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/v1/health")
    public ApiResponse<CareAdminHealthResponse> health() {
        return ApiResponse.success(statusService.health());
    }
}
