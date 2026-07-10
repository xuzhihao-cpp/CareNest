package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 17：订单取消或改期请求参数。
 */
public record OrderChangeRequest(String reason, String newScheduledStart) {
}
