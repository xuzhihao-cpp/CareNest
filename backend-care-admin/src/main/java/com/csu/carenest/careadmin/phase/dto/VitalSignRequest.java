package com.csu.carenest.careadmin.phase.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 阶段 14：生命体征提交请求参数。
 */
public record VitalSignRequest(
        @NotBlank String startTime,
        String endTime,
        String content,
        String nursingAdvice,
        Boolean abnormalFlag,
        Double temperature,
        Integer pulse,
        Integer breathRate,
        Integer systolicPressure,
        Integer diastolicPressure,
        Integer bloodOxygen,
        String remark
) {
}
