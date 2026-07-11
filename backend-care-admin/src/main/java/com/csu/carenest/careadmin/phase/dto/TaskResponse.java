package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 13：护理端任务工作台返回结构。
 */
public record TaskResponse(
        String taskId,
        String orderId,
        String nurseId,
        String nurseName,
        String elderName,
        String serviceName,
        String taskStatus,
        String orderStatus,
        String dispatchRemark,
        String scheduledStart
) {
}
