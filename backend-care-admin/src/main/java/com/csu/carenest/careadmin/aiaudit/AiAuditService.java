package com.csu.carenest.careadmin.aiaudit;

import com.csu.carenest.careadmin.auth.CurrentUser;
import com.csu.carenest.careadmin.auth.RoleCode;
import com.csu.carenest.careadmin.common.BusinessRuleException;
import com.csu.carenest.careadmin.common.ForbiddenException;
import com.csu.carenest.careadmin.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AiAuditService {
    private static final Set<String> LEVELS = Set.of("NORMAL", "WARNING", "CRITICAL");
    private final AiAuditRepository repository;

    public AiAuditService(AiAuditRepository repository) {
        this.repository = repository;
    }

    public AiAuditDtos.PageResult<AiAuditDtos.SessionItem> list(
            CurrentUser user, Boolean riskFlag, String safetyLevel, int page, int size) {
        requireAccess(user);
        if (page < 1 || size < 1 || size > 100
                || (safetyLevel != null && !safetyLevel.isBlank()
                && !LEVELS.contains(safetyLevel.toUpperCase(java.util.Locale.ROOT)))) {
            throw new BusinessRuleException();
        }
        return new AiAuditDtos.PageResult<>(
                repository.list(riskFlag, safetyLevel, size, (page - 1) * size),
                repository.count(riskFlag, safetyLevel), page, size);
    }

    public AiAuditDtos.SessionDetail detail(CurrentUser user, String sessionId) {
        requireAccess(user);
        AiAuditDtos.SessionItem session = repository.find(sessionId)
                .orElseThrow(NotFoundException::new);
        return new AiAuditDtos.SessionDetail(session, repository.messages(sessionId));
    }

    private void requireAccess(CurrentUser user) {
        boolean role = user.hasRole(RoleCode.ADMIN) || user.hasRole(RoleCode.CUSTOMER_SERVICE);
        if (!role || !repository.hasPermission(user.userId())) throw new ForbiddenException();
    }
}
