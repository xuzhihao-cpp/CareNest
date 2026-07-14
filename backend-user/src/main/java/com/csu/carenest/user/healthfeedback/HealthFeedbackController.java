package com.csu.carenest.user.healthfeedback;

import com.csu.carenest.user.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class HealthFeedbackController {
    private final HealthFeedbackService service;
    public HealthFeedbackController(HealthFeedbackService service) { this.service = service; }

    @PostMapping("/elder/health-feedback")
    public ApiResponse<HealthFeedbackDtos.CreateResult> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody HealthFeedbackDtos.CreateRequest request) {
        return ApiResponse.success(service.create(authorization, request));
    }

    @GetMapping("/family/elders/{elderId}/health-feedback")
    public ApiResponse<HealthFeedbackDtos.PageResult<HealthFeedbackDtos.Item>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("elderId") String elderId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "feedbackType", required = false) String feedbackType,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.success(service.list(authorization, elderId, page, size,
                feedbackType, severity, dateFrom, dateTo));
    }
}
