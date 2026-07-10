package com.csu.carenest.user.flow;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BindingController {

    private final UserSideFlowService flowService;

    public BindingController(UserSideFlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping("/family/bindings")
    public ApiResponse<BindingResponse> createBinding(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BindingRequest request) {
        return ApiResponse.success(flowService.createBinding(authorization, request));
    }

    @GetMapping("/family/bindings")
    public ApiResponse<List<BindingResponse>> familyBindings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(flowService.familyBindings(authorization));
    }

    @PostMapping("/elder/bindings/{bindingId}/approve")
    public ApiResponse<BindingResponse> approveBinding(
            @PathVariable("bindingId") String bindingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BindingRequest request) {
        return ApiResponse.success(flowService.approveBinding(authorization, bindingId, request));
    }

    @PutMapping("/family/bindings/{bindingId}/scopes")
    public ApiResponse<BindingResponse> updateBindingScopes(
            @PathVariable("bindingId") String bindingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BindingRequest request) {
        return ApiResponse.success(flowService.updateBindingScopes(authorization, bindingId, request));
    }

    @PostMapping("/family/bindings/{bindingId}/revoke")
    public ApiResponse<BindingResponse> revokeBinding(
            @PathVariable("bindingId") String bindingId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BindingRequest request) {
        return ApiResponse.success(flowService.revokeBinding(authorization, bindingId, request));
    }
}
