package com.csu.carenest.careadmin.phase.entity;

/**
 * 病历文件审核所需的最小领域实体，不接管成员1的数据库建模职责。
 */
public record MedicalFileEntity(
        String medicalFileId,
        String fileId,
        String elderId,
        String fileType,
        String title,
        String occurredAt,
        String auditStatus,
        String reviewComment) {
}
