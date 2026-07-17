package com.csu.carenest.careadmin.delivery;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 阶段51-55成员3管理端随访、看板和演示数据接口。 */
@RestController
@RequestMapping("/api/v1")
public class Phase51To55DeliveryController {

    private final AuthService authService;
    private final Phase51To55DeliveryService deliveryService;

    public Phase51To55DeliveryController(AuthService authService, Phase51To55DeliveryService deliveryService) {
        this.authService = authService;
        this.deliveryService = deliveryService;
    }

    @PostMapping("/admin/follow-ups")
    public ApiResponse<DeliveryDtos.FollowUpResponse> createFollowUp(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody DeliveryDtos.FollowUpRequest request) {
        return ApiResponse.success(deliveryService.createFollowUp(adminUser(authorization), request));
    }

    @GetMapping("/elders/{elderId}/follow-ups")
    public ApiResponse<List<DeliveryDtos.FollowUpRecordResponse>> familyFollowUps(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId) {
        return ApiResponse.success(deliveryService.familyFollowUps(
                authService.requireRole(authorization, RoleCode.FAMILY), elderId));
    }

    @GetMapping("/admin/dashboard/basic-statistics")
    public ApiResponse<DeliveryDtos.BasicStatisticsResponse> basicStatistics(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("dateFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam("dateTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.success(deliveryService.basicStatistics(
                adminUser(authorization), dateFrom, dateTo));
    }

    @GetMapping("/admin/dashboard/quality-statistics")
    public ApiResponse<DeliveryDtos.QualityStatisticsResponse> qualityStatistics(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("dateFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam("dateTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.success(deliveryService.qualityStatistics(
                adminUser(authorization), dateFrom, dateTo));
    }

    @PostMapping("/admin/demo-data/reset")
    public ApiResponse<DeliveryDtos.DemoDataStatusResponse> resetDemoData(
            @RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(deliveryService.resetDemoData(adminUser(authorization)));
    }

    private CurrentUser adminUser(String authorization) {
        return authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
