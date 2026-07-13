package com.csu.carenest.careadmin.phase.entity;

/**
 * 护理资质申请实体。
 */
public record QualificationApplicationEntity(
        String applicationId,
        String nurseId,
        String auditStatus,
        String reviewComment) {
}
