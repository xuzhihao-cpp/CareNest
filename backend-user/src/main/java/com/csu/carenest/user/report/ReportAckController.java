package com.csu.carenest.user.report;

import com.csu.carenest.user.common.ApiResponse;
import com.csu.carenest.user.auth.RoleCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ReportAckController {

    private final ReportAckService reportAckService;

    public ReportAckController(ReportAckService reportAckService) {
        this.reportAckService = reportAckService;
    }

    @GetMapping("/elder/reports/pending")
    public ApiResponse<List<PendingReportResponse>> elderPendingReports(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(reportAckService.pendingReports(authorization, RoleCode.ELDER));
    }

    @GetMapping("/elder/reports")
    public ApiResponse<List<PendingReportResponse>> elderReports(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(reportAckService.reports(authorization, RoleCode.ELDER, false));
    }

    @GetMapping("/family/reports/pending")
    public ApiResponse<List<PendingReportResponse>> familyPendingReports(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(reportAckService.pendingReports(authorization, RoleCode.FAMILY));
    }

    @GetMapping("/family/reports")
    public ApiResponse<List<PendingReportResponse>> familyReports(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(reportAckService.reports(authorization, RoleCode.FAMILY, false));
    }

    @PostMapping("/elder/reports/{reportId}/ack")
    public ApiResponse<ReportAckResponse> elderAck(
            @PathVariable("reportId") String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReportAckRequest request) {
        return ApiResponse.success(reportAckService.elderAck(authorization, reportId, request));
    }

    @PostMapping("/family/reports/{reportId}/ack")
    public ApiResponse<ReportAckResponse> familyAck(
            @PathVariable("reportId") String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReportAckRequest request) {
        return ApiResponse.success(reportAckService.familyAck(authorization, reportId, request));
    }

    @PostMapping("/family/reports/{reportId}/archive-suggestions/decision")
    public ApiResponse<ReportAckResponse> decideArchiveSuggestions(
            @PathVariable("reportId") String reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReportAckRequest request) {
        return ApiResponse.success(reportAckService.decideArchiveSuggestions(authorization, reportId, request));
    }
}
