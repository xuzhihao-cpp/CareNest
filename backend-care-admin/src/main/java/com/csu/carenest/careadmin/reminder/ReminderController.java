package com.csu.carenest.careadmin.reminder;

import com.csu.carenest.careadmin.auth.AuthService;
import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nurse/elders/{elderId}/reminders")
public class ReminderController {

    private final AuthService authService;
    private final ReminderService service;

    public ReminderController(AuthService authService, ReminderService service) {
        this.authService = authService;
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ReminderDtos.PageResult<ReminderDtos.ReminderItem>> list(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(service.list(currentUser, elderId, status, page, size));
    }

    @PostMapping
    public ApiResponse<ReminderDtos.ReminderItem> create(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId,
            @Valid @RequestBody ReminderDtos.ReminderUpsertRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(service.create(currentUser, elderId, request));
    }

    @PutMapping("/{reminderId}")
    public ApiResponse<ReminderDtos.ReminderItem> update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId,
            @PathVariable("reminderId") String reminderId,
            @Valid @RequestBody ReminderDtos.ReminderUpsertRequest request) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(service.update(currentUser, elderId, reminderId, request));
    }

    @DeleteMapping("/{reminderId}")
    public ApiResponse<ReminderDtos.ReminderDeleteResponse> delete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("elderId") String elderId,
            @PathVariable("reminderId") String reminderId) {
        CurrentUser currentUser = authService.requireRole(authorization, RoleCode.NURSE);
        return ApiResponse.success(service.delete(currentUser, elderId, reminderId));
    }
}
