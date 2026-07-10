package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 阶段 8：管理端新增或编辑服务项目的请求参数。
 */
public record ServiceItemRequest(
        @NotBlank String serviceName,
        String category,
        @NotNull @Min(0) Integer price,
        @NotNull @Min(1) Integer durationMinutes,
        String status
) {
}
