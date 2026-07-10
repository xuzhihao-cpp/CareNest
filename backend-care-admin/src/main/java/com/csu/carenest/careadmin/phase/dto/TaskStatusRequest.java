package com.csu.carenest.careadmin.phase.dto;

/**
 * 阶段 13：护理任务状态更新请求参数。
 */
public record TaskStatusRequest(String targetStatus, String dispatchRemark, String nurseId) {
}
