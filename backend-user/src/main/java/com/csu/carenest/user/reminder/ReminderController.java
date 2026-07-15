package com.csu.carenest.user.reminder;

import com.csu.carenest.user.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/elder/reminders")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service = service; }

    @GetMapping
    public ApiResponse<ReminderDtos.PageResult<ReminderDtos.Item>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.list(authorization, status, page, size));
    }

    @PostMapping("/{reminderId}/actions")
    public ApiResponse<ReminderDtos.ActionResult> act(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("reminderId") String reminderId, @RequestBody ReminderDtos.ActionRequest request) {
        return ApiResponse.success(service.act(authorization, reminderId, request));
    }

    @GetMapping("/records")
    public ApiResponse<ReminderDtos.PageResult<ReminderDtos.RecordItem>> records(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.records(authorization, page, size));
    }
}
