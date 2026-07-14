package com.csu.carenest.user.medicalfile;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

@Service
public class MedicalFileService {
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final AuthService authService;
    private final MedicalFileStorage storage;
    private final MedicalFileRepository repository;
    private final HomeCacheInvalidator homeCacheInvalidator;
    private static final Set<String> FILE_TYPES = Set.of(
            "PRESCRIPTION", "EXAMINATION_REPORT", "DISCHARGE_SUMMARY", "MEDICAL_RECORD");

    public MedicalFileService(AuthService authService, MedicalFileStorage storage,
                              MedicalFileRepository repository, HomeCacheInvalidator homeCacheInvalidator) {
        this.authService = authService;
        this.storage = storage;
        this.repository = repository;
        this.homeCacheInvalidator = homeCacheInvalidator;
    }

    @Transactional
    public MedicalFileDtos.RegisterResult register(String authorization, String elderId,
                                                   MedicalFileDtos.RegisterRequest request) {
        AuthService.CurrentUser user = authService.requireCurrentUser(authorization);
        if (!user.roles().contains(RoleCode.FAMILY) || !hasScope(user.userId(), elderId, "HEALTH_EDIT")) {
            throw new ApiException(403, "无权登记该长辈病历资料");
        }
        MedicalFileRepository.ElderRow elder = repository.findElder(elderId)
                .orElseThrow(() -> new ApiException(404, "长辈不存在"));
        if (!FILE_TYPES.contains(request.fileType())) throw new ApiException(422, "病历资料类型不支持");
        if (request.occurredAt().isAfter(LocalDate.now())) throw new ApiException(422, "发生日期不能晚于今天");
        MedicalFileRepository.AssetRow asset = repository.findAsset(request.fileId())
                .orElseThrow(() -> new ApiException(404, "文件不存在"));
        if (!user.userId().equals(asset.uploadedBy())) throw new ApiException(403, "文件不属于当前用户");
        if (repository.isAssetBound(request.fileId())) throw new ApiException(409, "文件已登记");
        String id = UUID.randomUUID().toString().replace("-", "");
        repository.insertMedicalFile(id, elderId, request, user.userId());
        repository.insertRegisterLog(UUID.randomUUID().toString().replace("-", ""), user.userId(), "FAMILY", id);
        if (elder.userId() != null) homeCacheInvalidator.evictAfterCommit(RoleCode.ELDER.name(), elder.userId());
        repository.findActiveFamilyIds(elderId).forEach(
                familyId -> homeCacheInvalidator.evictAfterCommit(RoleCode.FAMILY.name(), familyId));
        return new MedicalFileDtos.RegisterResult(id, request.fileId(), "PENDING");
    }

    public List<MedicalFileDtos.MedicalFileItem> list(String authorization, String elderId) {
        AuthService.CurrentUser user = authService.requireCurrentUser(authorization);
        MedicalFileRepository.ElderRow elder = repository.findElder(elderId)
                .orElseThrow(() -> new ApiException(404, "长辈不存在"));
        boolean elderSelf = user.roles().contains(RoleCode.ELDER) && user.userId().equals(elder.userId());
        boolean familyView = user.roles().contains(RoleCode.FAMILY) && hasScope(user.userId(), elderId, "HEALTH_VIEW");
        if (!elderSelf && !familyView) throw new ApiException(403, "无权查看该长辈病历资料");
        return repository.findMedicalFiles(elderId).stream().map(row -> {
            String url = storage.presignedGet(row.objectKey(), Duration.ofMinutes(10));
            return new MedicalFileDtos.MedicalFileItem(row.medicalFileId(), row.fileId(), row.auditStatus(),
                    row.fileType(), row.title(), row.occurredAt(), row.createdAt(), row.originalName(),
                    row.fileSize(), row.reviewComment(), url, url);
        }).toList();
    }

    private boolean hasScope(String familyId, String elderId, String scope) {
        return repository.findActiveScopes(familyId, elderId).stream()
                .anyMatch(value -> value != null && value.contains("\"" + scope + "\""));
    }

    @Transactional
    public MedicalFileDtos.UploadResult upload(String authorization, MultipartFile file) {
        AuthService.CurrentUser user = authService.requireCurrentUser(authorization);
        ValidatedFile validated = validate(file);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String objectKey = "medical/" + LocalDate.now() + "/" + fileId + validated.extension();
        boolean stored = false;
        try {
            storage.put(objectKey, validated.mimeType(), file);
            stored = true;
            repository.insertAsset(fileId, validated.originalName(), validated.mimeType(), file.getSize(), objectKey, user.userId());
            String role = user.roles().isEmpty() ? null : user.roles().get(0).name();
            repository.insertUploadLog(UUID.randomUUID().toString().replace("-", ""), user.userId(), role, fileId);
            String url = storage.presignedGet(objectKey, Duration.ofMinutes(10));
            return new MedicalFileDtos.UploadResult(fileId, url, validated.originalName(),
                    validated.mimeType(), file.getSize(), "PENDING");
        } catch (RuntimeException exception) {
            if (stored) {
                try {
                    storage.remove(objectKey);
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new ApiException(500, "文件上传失败，请稍后重试", exception);
        }
    }

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(422, "文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(422, "文件不能超过20MB");
        }
        String original = sanitize(file.getOriginalFilename());
        try {
            byte[] header = file.getInputStream().readNBytes(8);
            String detected = detect(header);
            String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!detected.equals(declared)) {
                throw new ApiException(422, "文件类型与内容不一致");
            }
            if (!hasMatchingExtension(original, detected)) {
                throw new ApiException(422, "文件扩展名与内容不一致");
            }
            return new ValidatedFile(original, detected, extension(detected));
        } catch (IOException exception) {
            throw new ApiException(422, "无法读取文件");
        }
    }

    private String detect(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') return "application/pdf";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && (bytes[4] & 0xff) == 0x0d
                && (bytes[5] & 0xff) == 0x0a
                && (bytes[6] & 0xff) == 0x1a
                && (bytes[7] & 0xff) == 0x0a) return "image/png";
        throw new ApiException(422, "仅支持PDF、JPEG和PNG文件");
    }

    private boolean hasMatchingExtension(String fileName, String mimeType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return switch (mimeType) {
            case "application/pdf" -> lowerName.endsWith(".pdf");
            case "image/jpeg" -> lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
            case "image/png" -> lowerName.endsWith(".png");
            default -> false;
        };
    }

    private String sanitize(String name) {
        String value = name == null ? "upload" : name.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t]", "_").trim();
        if (value.isBlank()) value = "upload";
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String extension(String mime) {
        return switch (mime) { case "application/pdf" -> ".pdf"; case "image/jpeg" -> ".jpg"; default -> ".png"; };
    }

    private record ValidatedFile(String originalName, String mimeType, String extension) {
    }
}
