package com.csu.carenest.user.flow;

import com.csu.carenest.user.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class ServiceAddressController {

    private final UserSideFlowService flowService;

    public ServiceAddressController(UserSideFlowService flowService) {
        this.flowService = flowService;
    }

    @GetMapping("/elders/{elderId}/service-addresses")
    public ApiResponse<List<ServiceAddressResponse>> serviceAddresses(
            @PathVariable("elderId") String elderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(flowService.serviceAddresses(authorization, elderId));
    }

    @PostMapping("/elders/{elderId}/service-addresses")
    public ApiResponse<ServiceAddressResponse> createServiceAddress(
            @PathVariable("elderId") String elderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ServiceAddressRequest request) {
        return ApiResponse.success(flowService.createServiceAddress(authorization, elderId, request));
    }

    @PutMapping("/service-addresses/{addressId}")
    public ApiResponse<ServiceAddressResponse> updateServiceAddress(
            @PathVariable("addressId") String addressId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ServiceAddressRequest request) {
        return ApiResponse.success(flowService.updateServiceAddress(authorization, addressId, request));
    }

    @DeleteMapping("/service-addresses/{addressId}")
    public ApiResponse<ServiceAddressResponse> deleteServiceAddress(
            @PathVariable("addressId") String addressId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(flowService.deleteServiceAddress(authorization, addressId));
    }
}
