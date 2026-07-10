package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 阶段 12：管理端派单请求参数。
 */
public record DispatchRequest(
        @NotBlank String nurseId,
        String dispatchRemark,
        String targetStatus
) {
}
