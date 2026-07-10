package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 12-13：派单、接单、任务状态更新后的返回结构。
 */
public record DispatchResponse(String orderId, String orderStatus, String taskId) {
}
