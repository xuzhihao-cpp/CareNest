package com.csu.carenest.user.flow;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ElderProfileController {

    private final UserSideFlowService flowService;

    public ElderProfileController(UserSideFlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping("/family/elders")
    public ApiResponse<List<ElderProfileResponse>> familyElders(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(flowService.familyElders(authorization));
    }

    @GetMapping("/elders/{elderId}/profile")
    public ApiResponse<ElderProfileResponse> elderProfile(
            @PathVariable("elderId") String elderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(flowService.elderProfile(authorization, elderId));
    }

    @PutMapping("/elders/{elderId}/profile")
    public ApiResponse<ElderProfileResponse> updateElderProfile(
            @PathVariable("elderId") String elderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ElderProfileRequest request) {
        return ApiResponse.success(flowService.updateElderProfile(authorization, elderId, request));
    }
}
