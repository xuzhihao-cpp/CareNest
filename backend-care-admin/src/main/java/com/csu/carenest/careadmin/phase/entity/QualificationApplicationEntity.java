package com.csu.carenest.careadmin.phase.entity;

/**
 * 护理资质申请实体。
 */
public record QualificationApplicationEntity(
        String applicationId,
        String nurseId,
        String auditStatus,
        String reviewComment,
        String nurseName,
        String realName,
        String idNoMasked,
        String certificateNo,
        String serviceSkillCodes,
        String submittedAt,
        String reviewedAt) {

    public QualificationApplicationEntity(
            String applicationId,
            String nurseId,
            String auditStatus,
            String reviewComment) {
        this(applicationId, nurseId, auditStatus, reviewComment,
                null, null, null, null, null, null, null);
    }
}
