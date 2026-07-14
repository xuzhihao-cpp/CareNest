package com.csu.carenest.user.medicalfile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MedicalFileRepository {
    private final JdbcTemplate jdbc;
    private final String bucket;

    public MedicalFileRepository(JdbcTemplate jdbc, @Value("${carenest.minio.bucket}") String bucket) {
        this.jdbc = jdbc;
        this.bucket = bucket;
    }

    public void insertAsset(String fileId, String originalName, String mimeType, long size,
                            String objectKey, String uploadedBy) {
        jdbc.update("""
                INSERT INTO file_asset
                  (file_id, original_name, mime_type, file_size, storage_bucket, object_key, audit_status, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, fileId, originalName, mimeType, size, bucket, objectKey, uploadedBy);
    }

    public void insertUploadLog(String logId, String userId, String roleCode, String fileId) {
        jdbc.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id)
                VALUES (?, ?, ?, 'UPLOAD_MEDICAL_FILE_ASSET', 'FILE_ASSET', ?, ?, ?)
                """, logId, userId, roleCode, fileId, "{\"auditStatus\":\"PENDING\"}", logId);
    }

    public Optional<ElderRow> findElder(String elderId) {
        return jdbc.query("SELECT elder_id, user_id FROM elder_profile WHERE elder_id = ?",
                (rs, n) -> new ElderRow(rs.getString("elder_id"), rs.getString("user_id")), elderId).stream().findFirst();
    }

    public List<String> findActiveScopes(String familyId, String elderId) {
        return jdbc.queryForList("""
                SELECT scope_codes FROM elder_family_binding
                WHERE family_id = ? AND elder_id = ? AND binding_status = 'ACTIVE'
                """, String.class, familyId, elderId);
    }

    public List<String> findActiveFamilyIds(String elderId) {
        return jdbc.queryForList("""
                SELECT DISTINCT family_id FROM elder_family_binding
                WHERE elder_id = ? AND binding_status = 'ACTIVE'
                """, String.class, elderId);
    }

    public Optional<AssetRow> findAsset(String fileId) {
        return jdbc.query("""
                SELECT file_id, original_name, mime_type, file_size, object_key, uploaded_by, audit_status
                FROM file_asset WHERE file_id = ?
                """, (rs, n) -> new AssetRow(rs.getString("file_id"), rs.getString("original_name"),
                rs.getString("mime_type"), rs.getLong("file_size"), rs.getString("object_key"),
                rs.getString("uploaded_by"), rs.getString("audit_status")), fileId).stream().findFirst();
    }

    public boolean isAssetBound(String fileId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM medical_file WHERE file_id = ?", Integer.class, fileId) > 0;
    }

    public void insertMedicalFile(String id, String elderId, MedicalFileDtos.RegisterRequest request, String uploaderId) {
        jdbc.update("""
                INSERT INTO medical_file
                  (medical_file_id, elder_id, file_id, file_type, title, occurred_at, audit_status, uploader_id)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, id, elderId, request.fileId(), request.fileType(), request.title().trim(), request.occurredAt(), uploaderId);
    }

    public void insertRegisterLog(String logId, String userId, String roleCode, String id) {
        jdbc.update("""
                INSERT INTO operation_log
                  (log_id, operator_id, role_code, operation_type, biz_type, biz_id, after_value, trace_id)
                VALUES (?, ?, ?, 'REGISTER_MEDICAL_FILE', 'MEDICAL_FILE', ?, ?, ?)
                """, logId, userId, roleCode, id, "{\"auditStatus\":\"PENDING\"}", logId);
    }

    public List<MedicalRow> findMedicalFiles(String elderId) {
        return jdbc.query("""
                SELECT m.medical_file_id, m.file_id, m.file_type, m.title, m.occurred_at,
                       m.audit_status, m.review_comment, m.created_at,
                       f.original_name, f.file_size, f.object_key
                FROM medical_file m JOIN file_asset f ON f.file_id = m.file_id
                WHERE m.elder_id = ? ORDER BY m.created_at DESC, m.medical_file_id DESC
                """, (rs, n) -> new MedicalRow(rs.getString("medical_file_id"), rs.getString("file_id"),
                rs.getString("file_type"), rs.getString("title"), rs.getObject("occurred_at", LocalDate.class),
                rs.getString("audit_status"), rs.getString("review_comment"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("original_name"),
                rs.getLong("file_size"), rs.getString("object_key")), elderId);
    }

    public record ElderRow(String elderId, String userId) {}
    public record AssetRow(String fileId, String originalName, String mimeType, long fileSize,
                           String objectKey, String uploadedBy, String auditStatus) {}
    public record MedicalRow(String medicalFileId, String fileId, String fileType, String title,
                             LocalDate occurredAt, String auditStatus, String reviewComment,
                             LocalDateTime createdAt, String originalName, long fileSize, String objectKey) {}
}
