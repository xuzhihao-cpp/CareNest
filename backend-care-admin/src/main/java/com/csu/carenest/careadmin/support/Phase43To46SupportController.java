package com.csu.carenest.careadmin.support;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 阶段43-46成员3后端接口；长辈人工协助入口仍由成员2负责。 */
@RestController
@RequestMapping("/api/v1")
public class Phase43To46SupportController {

    private final AuthService authService;
    private final Phase43To46SupportService supportService;

    public Phase43To46SupportController(AuthService authService, Phase43To46SupportService supportService) {
        this.authService = authService;
        this.supportService = supportService;
    }

    @PostMapping("/customer-service/tickets")
    public ApiResponse<SupportDtos.TicketResponse> createTicket(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SupportDtos.TicketRequest request) {
        return ApiResponse.success(supportService.createTicket(
                authService.requireCurrentUser(authorization), request));
    }

    @GetMapping("/admin/customer-service/tickets")
    public ApiResponse<List<SupportDtos.TicketResponse>> tickets(
            @RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(supportService.tickets(adminUser(authorization)));
    }

    @PostMapping("/admin/customer-service/tickets/{ticketId}/reply")
    public ApiResponse<SupportDtos.TicketResponse> reply(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody SupportDtos.TicketRequest request) {
        return ApiResponse.success(supportService.reply(adminUser(authorization), ticketId, request));
    }

    @PostMapping("/admin/customer-service/tickets/{ticketId}/close")
    public ApiResponse<SupportDtos.TicketResponse> close(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody SupportDtos.TicketRequest request) {
        return ApiResponse.success(supportService.close(adminUser(authorization), ticketId, request));
    }

    @PostMapping("/admin/customer-service/tickets/{ticketId}/follow-up")
    public ApiResponse<SupportDtos.FollowUpResponse> addFollowUp(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody SupportDtos.FollowUpRequest request) {
        return ApiResponse.success(supportService.addFollowUp(adminUser(authorization), ticketId, request));
    }

    @GetMapping("/admin/customer-service/tickets/{ticketId}/follow-ups")
    public ApiResponse<List<SupportDtos.FollowUpResponse>> followUps(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("ticketId") String ticketId) {
        return ApiResponse.success(supportService.followUps(adminUser(authorization), ticketId));
    }

    @PostMapping("/family/orders/{orderId}/reviews")
    public ApiResponse<SupportDtos.ReviewComplaintResponse> submitReview(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody SupportDtos.ReviewComplaintRequest request) {
        CurrentUser user = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(supportService.submitReview(user, orderId, request));
    }

    @PostMapping("/family/orders/{orderId}/complaints")
    public ApiResponse<SupportDtos.ReviewComplaintResponse> submitComplaint(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody SupportDtos.ReviewComplaintRequest request) {
        CurrentUser user = authService.requireRole(authorization, RoleCode.FAMILY);
        return ApiResponse.success(supportService.submitComplaint(user, orderId, request));
    }

    @GetMapping("/admin/complaints")
    public ApiResponse<List<SupportDtos.ReviewComplaintResponse>> complaints(
            @RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(supportService.complaints(adminUser(authorization)));
    }

    @PostMapping("/admin/complaints/{complaintId}/handle")
    public ApiResponse<SupportDtos.ReviewComplaintResponse> handleComplaint(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("complaintId") String complaintId,
            @Valid @RequestBody SupportDtos.ReviewComplaintRequest request) {
        return ApiResponse.success(supportService.handleComplaint(
                adminUser(authorization), complaintId, request));
    }

    @PostMapping("/nurse/appeals")
    public ApiResponse<SupportDtos.AppealResponse> submitAppeal(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody SupportDtos.AppealRequest request) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(supportService.submitAppeal(user, request));
    }

    @GetMapping("/nurse/appeals")
    public ApiResponse<List<SupportDtos.AppealResponse>> appeals(
            @RequestHeader("Authorization") String authorization) {
        CurrentUser user = authService.requireAnyRole(authorization, RoleCode.NURSE, RoleCode.ADMIN);
        return ApiResponse.success(supportService.appeals(user));
    }

    @PostMapping("/admin/nurse-appeals/{appealId}/review")
    public ApiResponse<SupportDtos.AppealResponse> reviewAppeal(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("appealId") String appealId,
            @Valid @RequestBody SupportDtos.AppealRequest request) {
        return ApiResponse.success(supportService.reviewAppeal(
                adminUser(authorization), appealId, request));
    }

    private CurrentUser adminUser(String authorization) {
        return authService.requireAnyRole(authorization, RoleCode.ADMIN, RoleCode.CUSTOMER_SERVICE);
    }
}
