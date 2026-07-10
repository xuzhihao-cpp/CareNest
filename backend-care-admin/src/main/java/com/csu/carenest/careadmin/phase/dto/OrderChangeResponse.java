package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 17：订单取消或改期后的返回结构。
 */
public record OrderChangeResponse(String orderId, String orderStatus, String scheduledStart) {
}
