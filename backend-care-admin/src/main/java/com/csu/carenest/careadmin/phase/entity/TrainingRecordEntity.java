package com.csu.carenest.careadmin.phase.entity;

/**
 * 护理培训资格实体。
 */
public record TrainingRecordEntity(
        String nurseId,
        String nurseName,
        String qualificationStatus,
        String trainingStatus,
        String trainingBatch,
        String passedAt,
        String expiredAt,
        String remark) {

    public TrainingRecordEntity(String nurseId, String trainingStatus, String expiredAt) {
        this(nurseId, null, null, trainingStatus, null, null, expiredAt, null);
    }
}
