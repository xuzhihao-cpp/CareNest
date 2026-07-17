package com.csu.carenest.user.healthfeedback;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.ai.AiAssistantService;
import com.csu.carenest.user.common.ApiException;
import com.csu.carenest.user.medicalfile.MedicalFileStorage;
import com.csu.carenest.user.redis.HomeCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class HealthFeedbackService {
    private static final long MAX_VOICE_SIZE = 12L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("PAIN", "DIZZINESS", "SLEEP", "DIET", "MENTAL_STATE");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> INPUTS = Set.of("BUTTON", "TEXT", "VOICE");
    private final AuthService auth;
    private final HealthFeedbackRepository repository;
    private final MedicalFileStorage storage;
    private final HomeCacheInvalidator cache;
    private final AiAssistantService aiAssistantService;

    public HealthFeedbackService(AuthService auth, HealthFeedbackRepository repository,
                                 MedicalFileStorage storage, HomeCacheInvalidator cache,
                                 AiAssistantService aiAssistantService) {
        this.auth = auth; this.repository = repository; this.storage = storage; this.cache = cache;
        this.aiAssistantService = aiAssistantService;
    }

    @Transactional
    public HealthFeedbackDtos.CreateResult create(String authorization, HealthFeedbackDtos.CreateRequest request) {
        AuthService.CurrentUser user = auth.requireCurrentUser(authorization);
        if (!user.roles().contains(RoleCode.ELDER)) throw new ApiException(403, "仅长辈本人可以提交健康反馈");
        HealthFeedbackRepository.ElderRow elder = repository.findElderByUser(user.userId())
                .orElseThrow(() -> new ApiException(404, "未找到当前长辈档案"));
        validate(request);
        HealthFeedbackRepository.AssetRow asset = null;
        if ("VOICE".equals(request.inputType())) {
            asset = repository.findAsset(request.fileId()).orElseThrow(() -> new ApiException(404, "语音文件不存在"));
            if (!user.userId().equals(asset.uploadedBy())) throw new ApiException(403, "语音文件不属于当前长辈");
            if (!asset.mimeType().startsWith("audio/")) throw new ApiException(422, "文件不是受支持的语音格式");
            if (asset.fileSize() > MAX_VOICE_SIZE) throw new ApiException(422, "语音文件不能超过12MB");
        }
        String feedbackId = id();
        repository.insert(feedbackId, elder.elderId(), request, user.userId());
        if (asset != null) repository.insertVoiceLog(id(), user.userId(), asset.fileId(), feedbackId);
        if ("HIGH".equals(request.severity())) repository.insertHighSeverityLog(id(), user.userId(), feedbackId);
        cache.evictAfterCommit(RoleCode.ELDER.name(), user.userId());
        repository.activeFamilyIds(elder.elderId()).forEach(id -> cache.evictAfterCommit(RoleCode.FAMILY.name(), id));
        String advice = aiAssistantService.healthFeedbackAdvice(
                request.feedbackType(), request.severity(), request.content());
        return new HealthFeedbackDtos.CreateResult(feedbackId, repository.createdAt(feedbackId), advice);
    }

    public HealthFeedbackDtos.PageResult<HealthFeedbackDtos.Item> list(
            String authorization, String elderId, int page, int size, String type, String severity,
            LocalDate from, LocalDate to) {
        AuthService.CurrentUser user = auth.requireCurrentUser(authorization);
        if (!user.roles().contains(RoleCode.FAMILY)
                || !repository.hasActiveScope(user.userId(), elderId, "HEALTH_VIEW")) {
            throw new ApiException(403, "当前家属无权查看该长辈健康反馈");
        }
        repository.findElder(elderId).orElseThrow(() -> new ApiException(404, "长辈不存在"));
        if (page < 1 || size < 1 || size > 50) throw new ApiException(422, "分页参数不合法");
        type = optional(type, TYPES, "反馈类型不合法");
        severity = optional(severity, SEVERITIES, "严重程度不合法");
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(422, "开始日期不能晚于结束日期");
        long total = repository.count(elderId, type, severity, from, to);
        var records = repository.list(elderId, type, severity, from, to, (page - 1) * size, size).stream()
                .map(row -> new HealthFeedbackDtos.Item(row.feedbackId(), row.elderId(), row.elderName(),
                        row.feedbackType(), row.severity(), row.content(), row.inputType(), row.fileId(),
                        row.objectKey() == null ? null : storage.presignedGet(row.objectKey(), Duration.ofMinutes(10)),
                        row.createdAt())).toList();
        return new HealthFeedbackDtos.PageResult<>(records, total, page, size);
    }

    private void validate(HealthFeedbackDtos.CreateRequest request) {
        if (request == null) throw new ApiException(422, "反馈内容不能为空");
        if (request.elderId() != null) throw new ApiException(400, "不能指定长辈，系统将从登录身份识别");
        required(request.feedbackType(), TYPES, "反馈类型不合法");
        required(request.severity(), SEVERITIES, "严重程度不合法");
        required(request.inputType(), INPUTS, "输入方式不合法");
        if (request.content() != null && request.content().trim().length() > 512) throw new ApiException(422, "补充说明不能超过512个字");
        boolean hasFile = request.fileId() != null && !request.fileId().isBlank();
        if ("VOICE".equals(request.inputType()) && !hasFile) throw new ApiException(422, "语音反馈必须提供文件");
        if (!"VOICE".equals(request.inputType()) && hasFile) throw new ApiException(422, "非语音反馈不能携带文件");
    }
    private String optional(String value, Set<String> values, String message) {
        if (value == null || value.isBlank()) return null;
        required(value, values, message); return value;
    }
    private void required(String value, Set<String> values, String message) {
        if (value == null || !values.contains(value)) throw new ApiException(422, message);
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
