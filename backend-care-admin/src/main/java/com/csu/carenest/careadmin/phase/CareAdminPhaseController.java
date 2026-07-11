package com.csu.carenest.careadmin.phase;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import com.csu.carenest.careadmin.common.PageData;
import com.csu.carenest.careadmin.phase.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 成员3在阶段8-18中的接口入口。
 * 只保留护理端与管理端后端主责接口，不实现成员1/2/4主责功能。
 */
@RestController
@RequestMapping("/api/v1")
public class CareAdminPhaseController {

    private final AuthService authService;
    private final CareAdminPhaseService phaseService;

    public CareAdminPhaseController(AuthService authService, CareAdminPhaseService phaseService) {
        this.authService = authService;
        this.phaseService = phaseService;
    }

    @GetMapping("/nurse/workbench-summary")
    public ApiResponse<HomeSummaryResponse> nurseWorkbenchSummary(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(phaseService.nurseWorkbenchSummary(currentUser));
    }

    @GetMapping("/admin/dashboard/overview")
    public ApiResponse<HomeSummaryResponse> adminDashboardOverview(
            @RequestHeader("Authorization") String authorization) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.adminDashboardOverview());
    }

    @GetMapping("/service-items")
    public ApiResponse<List<ServiceItemResponse>> serviceItems(@RequestHeader("Authorization") String authorization) {
        CurrentUser currentUser = authService.requireCurrentUser(authorization);
        boolean includeOffShelf = currentUser.hasRole(RoleCode.ADMIN) || currentUser.hasRole(RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.serviceItems(includeOffShelf));
    }

    @GetMapping("/service-items/{serviceId}")
    public ApiResponse<ServiceItemResponse> serviceItem(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("serviceId") String serviceId) {
        authService.requireCurrentUser(authorization);
        return ApiResponse.success(phaseService.serviceItem(serviceId));
    }

    @PostMapping("/admin/service-items")
    public ApiResponse<ServiceItemResponse> createServiceItem(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ServiceItemRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.createServiceItem(currentUser, request));
    }

    @PutMapping("/admin/service-items/{serviceId}")
    public ApiResponse<ServiceItemResponse> updateServiceItem(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("serviceId") String serviceId,
            @Valid @RequestBody ServiceItemRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.updateServiceItem(currentUser, serviceId, request));
    }

    @DeleteMapping("/admin/service-items/{serviceId}")
    public ApiResponse<ServiceItemResponse> deleteServiceItem(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("serviceId") String serviceId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.deleteServiceItem(currentUser, serviceId));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> orderDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireCurrentUser(authorization);
        return ApiResponse.success(phaseService.orderDetail(currentUser, orderId));
    }

    @GetMapping("/family/orders")
    public ApiResponse<PageData<OrderDetailResponse>> familyOrders(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(phaseService.familyOrders(currentUser, page, size));
    }

    @PostMapping("/family/orders")
    public ApiResponse<OrderDetailResponse> createFamilyOrder(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody FamilyOrderRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(phaseService.createFamilyOrder(currentUser, request));
    }

    @GetMapping("/admin/orders")
    public ApiResponse<PageData<OrderDetailResponse>> adminOrders(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "orderStatus", required = false) String orderStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.adminOrders(orderStatus, keyword, dateFrom, dateTo, page, size));
    }

    @GetMapping("/admin/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> adminOrderDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.orderDetail(currentUser, orderId));
    }

    @PostMapping("/admin/orders/{orderId}/dispatch")
    public ApiResponse<DispatchResponse> dispatchOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody DispatchRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.dispatchOrder(currentUser, orderId, request));
    }

    @PostMapping("/nurse/tasks/{taskId}/accept")
    public ApiResponse<DispatchResponse> acceptTask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("taskId") String taskId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.acceptTask(currentUser, taskId));
    }

    @PostMapping("/nurse/tasks/{taskId}/status")
    public ApiResponse<DispatchResponse> updateTaskStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("taskId") String taskId,
            @RequestBody TaskStatusRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.updateTaskStatus(currentUser, taskId, request));
    }

    @GetMapping("/nurse/tasks")
    public ApiResponse<PageData<TaskResponse>> nurseTasks(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.nurseTasks(currentUser, status, page, size));
    }

    @GetMapping("/nurse/tasks/{taskId}")
    public ApiResponse<TaskResponse> nurseTask(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("taskId") String taskId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.taskDetail(currentUser, taskId));
    }

    @PostMapping("/nurse/orders/{orderId}/service-records")
    public ApiResponse<ServiceRecordResponse> createServiceRecord(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody ServiceRecordRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.createServiceRecord(currentUser, orderId, request));
    }

    @PostMapping("/nurse/orders/{orderId}/vital-signs")
    public ApiResponse<ServiceRecordResponse> createVitalSign(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody VitalSignRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.createVitalSign(currentUser, orderId, request));
    }

    @GetMapping("/orders/{orderId}/service-records")
    public ApiResponse<List<Map<String, Object>>> serviceRecords(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireCurrentUser(authorization);
        return ApiResponse.success(phaseService.serviceRecords(currentUser, orderId));
    }

    @PostMapping("/orders/{orderId}/service-report/generate")
    public ApiResponse<ReportResponse> generateReport(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(phaseService.generateReport(currentUser, orderId));
    }

    @GetMapping("/orders/{orderId}/service-report")
    public ApiResponse<ReportResponse> report(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId) {
        CurrentUser currentUser = authService.requireCurrentUser(authorization);
        return ApiResponse.success(phaseService.report(currentUser, orderId));
    }

    @PostMapping("/family/orders/{orderId}/cancel")
    public ApiResponse<OrderChangeResponse> cancelFamilyOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @RequestBody OrderChangeRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(phaseService.cancelFamilyOrder(currentUser, orderId, request));
    }

    @PostMapping("/family/orders/{orderId}/reschedule")
    public ApiResponse<OrderChangeResponse> rescheduleFamilyOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @RequestBody OrderChangeRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(phaseService.rescheduleFamilyOrder(currentUser, orderId, request));
    }

    @PostMapping("/admin/orders/{orderId}/cancel")
    public ApiResponse<OrderChangeResponse> cancelAdminOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @RequestBody OrderChangeRequest request) {
        CurrentUser currentUser = authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.cancelAdminOrder(currentUser, orderId, request));
    }

    @GetMapping("/admin/demo-data/status")
    public ApiResponse<DemoDataStatusResponse> demoDataStatus(@RequestHeader("Authorization") String authorization) {
        authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
        return ApiResponse.success(phaseService.demoDataStatus());
    }
}
