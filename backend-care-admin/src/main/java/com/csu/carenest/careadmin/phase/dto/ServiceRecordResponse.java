package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 14：护理记录或生命体征提交后的返回结构。
 */
public record ServiceRecordResponse(
        String recordId,
        String orderId,
        String orderStatus
) {
}
