package com.csu.carenest.careadmin.phase.enums;

import com.csu.carenest.careadmin.common.BusinessRuleException;

import java.util.Locale;

/**
 * 文档冻结的统一审核状态。
 */
public enum AuditStatus {
    PENDING,
    APPROVED,
    REJECTED,
    NEED_MORE;

    public static AuditStatus parse(String value) {
        try {
            return AuditStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessRuleException();
        }
    }
}
