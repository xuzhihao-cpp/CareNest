package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 11：订单详情与管理端订单列表中的单条记录。
 */
public record OrderDetailResponse(
        String orderId,
        String orderNo,
        String orderStatus,
        String elderId,
        String familyId,
        String serviceId,
        String serviceName,
        String addressId,
        String serviceAddress,
        String scheduledStart,
        String scheduledEnd,
        Integer servicePrice,
        String contactName,
        String contactPhone,
        String preferredNurseName,
        String preferredNurseReason,
        String remark
) {
}
