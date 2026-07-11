package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 8：服务项目返回结构。
 */
public record ServiceItemResponse(
        String serviceId,
        String serviceName,
        String category,
        Integer price,
        Integer durationMinutes,
        String status
) {
}
