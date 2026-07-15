package com.csu.carenest.user.healtharchive;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;

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

    @GetMapping("/health-archive/change-logs")
    public ApiResponse<java.util.List<HealthArchiveDtos.ArchiveChangeLogResponse>> getArchiveChangeLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId) {
        return ApiResponse.success(service.getArchiveChangeLogs(authorization, elderId));
    }

    @PutMapping("/health-archive")
    public ApiResponse<HealthArchiveDtos.ArchiveUpdateResult> updateArchive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId,
            @Valid @RequestBody HealthArchiveDtos.ArchiveUpdateRequest request) {
        return ApiResponse.success(service.updateArchive(authorization, elderId, request));
    }

    @PostMapping("/medications")
    public ApiResponse<HealthArchiveDtos.MedicationCreateResult> addMedication(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId,
            @Valid @RequestBody HealthArchiveDtos.MedicationCreateRequest request) {
        return ApiResponse.success(service.addMedication(authorization, elderId, request));
    }
}
