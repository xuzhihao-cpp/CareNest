package com.csu.carenest.careadmin.phase.entity;

/**
 * 健康信息审核任务实体。
 */
public record HealthReviewTaskEntity(
        String taskId,
        String elderId,
        String status,
        String archiveVersion,
        String fieldName,
        String oldValue,
        String newValue,
        String sourceType,
        String sourceId,
        String reviewRemark) {
}
