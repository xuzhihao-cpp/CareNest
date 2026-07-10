package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 阶段 14：护理服务记录提交请求参数。
 */
public record ServiceRecordRequest(
        @NotBlank String startTime,
        String endTime,
        @NotBlank String content,
        String nursingAdvice,
        Boolean abnormalFlag
) {
}
