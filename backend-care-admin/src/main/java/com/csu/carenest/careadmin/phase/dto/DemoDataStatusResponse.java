package com.csu.carenest.careadmin.phase.dto;

import java.util.List;

/**
 * 阶段 18：演示数据就绪状态返回结构。
 */
public record DemoDataStatusResponse(Boolean ready, List<String> accounts, Integer scenarioCount) {
}
