package com.csu.carenest.user.ai;

import com.csu.carenest.user.auth.AuthService;
import com.csu.carenest.user.auth.RoleCode;
import com.csu.carenest.user.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiAssistantService {
    private final AuthService auth;
    private final AiAssistantRepository repo;
    private final AiProvider provider;
    private final AiTriageClassifier triageClassifier;

    public AiAssistantService(AuthService auth, AiAssistantRepository repo, AiProvider provider) {
        this(auth, repo, provider, new AiTriageClassifier());
    }

    public AiAssistantService(AuthService auth, AiAssistantRepository repo, AiProvider provider,
                              AiTriageClassifier triageClassifier) {
        this.auth = auth;
        this.repo = repo;
        this.provider = provider;
        this.triageClassifier = triageClassifier;
    }

    @Transactional
    public AiAssistantDtos.Session create(String token, AiAssistantDtos.CreateSessionRequest req) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        String elderId = resolve(user, req == null ? null : req.elderId());
        AiAssistantRepository.Elder elder = repo.elder(elderId)
                .orElseThrow(() -> new ApiException(404, "elder not found"));
        String id = "ai_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        repo.createSession(id, elderId, user.userId(), req == null ? null : req.sessionTitle(),
                req == null || req.sourceType() == null ? "TEXT" : req.sourceType(), id);
        return new AiAssistantDtos.Session(id, elder.id(), elder.name(),
                req == null ? null : req.sessionTitle(), "ACTIVE", "NORMAL", false,
                null, null, LocalDateTime.now().toString());
    }

    @Transactional
    public AiAssistantDtos.MessageResult message(String token, String sessionId,
                                                  AiAssistantDtos.MessageRequest req) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        authorizeSession(user, sessionId);
        if (req == null || req.content() == null || req.content().isBlank()) {
            throw new ApiException(422, "content is required");
        }

        AiAssistantRepository.TriageContext pending = repo.triageContext(sessionId).orElse(null);
        String evaluationText = pending != null && pending.awaitingAnswer()
                ? pending.context() + "\n用户补充：" + req.content()
                : req.content();
        AiTriageResult triage = triageClassifier.classify(evaluationText);
        String answer;
        String level = triage.level();
        String category = triage.category();
        String priority = triage.priority();
        if (triage.isFollowUp() || triage.isCritical() || "WARNING".equals(triage.level())) {
            answer = triage.answer();
        } else {
            AiProvider.Result cloud = provider.answer(evaluationText);
            answer = cloud.answer();
            level = cloud.safetyLevel();
            category = cloud.category();
            priority = cloud.priority();
        }

        String fingerprint = fingerprint(evaluationText, category);
        String trace = "trace_" + UUID.randomUUID().toString().replace("-", "");
        String userMessageId = messageId();
        String assistantMessageId = messageId();
        boolean risk = "CRITICAL".equals(level) || "WARNING".equals(level);
        repo.addMessage(userMessageId, sessionId, "USER",
                req.messageType() == null ? "TEXT" : req.messageType(),
                summary(req.content()), req.content(), req.voiceLogId(), risk, trace);
        repo.addMessage(assistantMessageId, sessionId, "ASSISTANT", "TEXT",
                summary(answer), answer, null, risk, trace);
        repo.updateSafety(sessionId, level, risk);

        if (triage.isFollowUp()) {
            repo.updateTriageContext(sessionId, triage, evaluationText, fingerprint);
        } else {
            repo.clearTriageContext(sessionId);
        }

        String elderId = repo.sessionElder(sessionId).orElseThrow();
        String assistanceId = null;
        boolean customerServiceCreated = false;
        if ("CRITICAL".equals(level) && !repo.hasUrgentFingerprint(sessionId, fingerprint)) {
            assistanceId = "assist_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            repo.assistance(assistanceId, elderId, user.userId(), sessionId, category, priority,
                    fingerprint + "：" + answer);
            String ticketId = "cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            repo.customerTicket(ticketId, assistanceId, elderId, user.userId(), category, priority, answer);
            customerServiceCreated = true;
        }

        return new AiAssistantDtos.MessageResult(sessionId, userMessageId, assistantMessageId,
                answer, level, risk, assistanceId, customerServiceCreated,
                triage.isFollowUp() ? triage.level() : null,
                triage.isFollowUp() ? triage.category() : null,
                triage.isFollowUp(), triage.isFollowUp() ? triage.followUpQuestion() : null);
    }

    public AiAssistantDtos.PageResult<AiAssistantDtos.AssistanceTicket> tickets(
            String token, String elderId, String status, int page, int size) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        String id = resolve(user, elderId);
        if (page < 1 || size < 1 || size > 50) throw new ApiException(422, "invalid page");
        return new AiAssistantDtos.PageResult<>(repo.tickets(id, status, size, (page - 1) * size),
                repo.ticketCount(id, status), page, size);
    }

    public AiAssistantDtos.PageResult<AiAssistantDtos.SessionSummary> listSessions(
            String token, String elderId, int page, int size) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        if (page < 1 || size < 1 || size > 50) throw new ApiException(422, "invalid page");
        if (user.roles().contains(RoleCode.ELDER)) {
            AiAssistantRepository.Elder elder = repo.elderByUser(user.userId())
                    .orElseThrow(() -> new ApiException(404, "elder not found"));
            return new AiAssistantDtos.PageResult<>(
                    repo.sessionsForElder(elder.id(), user.userId(), size, (page - 1) * size),
                    repo.sessionCountForElder(elder.id(), user.userId()), page, size);
        }
        if (user.roles().contains(RoleCode.FAMILY)) {
            if (elderId != null && !repo.bound(user.userId(), elderId)) {
                throw new ApiException(403, "active family binding required");
            }
            return new AiAssistantDtos.PageResult<>(
                    repo.sessionsForFamily(user.userId(), elderId, size, (page - 1) * size),
                    repo.sessionCountForFamily(user.userId(), elderId), page, size);
        }
        throw new ApiException(403, "role not allowed");
    }

    public List<AiAssistantDtos.ConversationMessage> messages(String token, String sessionId) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        authorizeSession(user, sessionId);
        return repo.messages(sessionId);
    }

    public String resolveAuthorizedElder(String token, String elderId) {
        return resolve(auth.requireCurrentUser(token), elderId);
    }

    public void close(String token, String session) {
        AuthService.CurrentUser user = auth.requireCurrentUser(token);
        authorizeSession(user, session);
        repo.close(session);
    }

    private String resolve(AuthService.CurrentUser user, String requested) {
        if (user.roles().contains(RoleCode.ELDER)) {
            String id = repo.elderByUser(user.userId())
                    .orElseThrow(() -> new ApiException(404, "elder not found")).id();
            if (requested != null && !requested.equals(id)) throw new ApiException(403, "not owner");
            return id;
        }
        if (user.roles().contains(RoleCode.FAMILY)) {
            if (requested == null || !repo.bound(user.userId(), requested)) {
                throw new ApiException(403, "active family binding required");
            }
            return requested;
        }
        throw new ApiException(403, "role not allowed");
    }

    private void authorizeSession(AuthService.CurrentUser user, String session) {
        String elder = repo.sessionElder(session)
                .orElseThrow(() -> new ApiException(404, "session not found"));
        if (user.roles().contains(RoleCode.ELDER)) {
            if (repo.sessionOwner(session).equals(user.userId())) return;
            resolve(user, elder);
            return;
        }
        if (user.roles().contains(RoleCode.FAMILY)) {
            resolve(user, elder);
            return;
        }
        throw new ApiException(403, "role not allowed");
    }

    private String fingerprint(String content, String category) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((category + "|" + content).getBytes(StandardCharsets.UTF_8));
            return category + ":" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            return category + ":" + Integer.toHexString(content.hashCode());
        }
    }

    private String summary(String content) {
        return content.length() > 480 ? content.substring(0, 480) : content;
    }

    private String messageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
    }
}
