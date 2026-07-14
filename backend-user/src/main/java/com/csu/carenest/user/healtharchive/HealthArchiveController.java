package com.csu.carenest.user.healtharchive;

import com.csu.carenest.user.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/elders/{elderId}")
public class HealthArchiveController {

    private final HealthArchiveService service;

    public HealthArchiveController(HealthArchiveService service) {
        this.service = service;
    }

    @GetMapping("/health-archive")
    public ApiResponse<HealthArchiveDtos.ArchiveResponse> getArchive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId) {
        return ApiResponse.success(service.getArchive(authorization, elderId));
    }
}
