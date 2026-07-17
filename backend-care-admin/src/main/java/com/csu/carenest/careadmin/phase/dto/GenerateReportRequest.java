package com.csu.carenest.careadmin.phase.dto;

/**
 * 管理端在生成服务报告前可确认的文字内容。
 * 服务记录和生命体征始终由护理留档生成，不能在这里改写。
 */
public record GenerateReportRequest(
        String summary,
        String nursingAdvice
) {
}
