package com.csu.carenest.careadmin.phase.dto;

import java.util.List;

/**
 * 阶段 15：服务报告返回结构。
 */
public record ReportResponse(
        String reportId,
        String orderId,
        String summary,
        List<String> vitalSigns,
        List<String> serviceRecords,
        String nursingAdvice
) {
}
