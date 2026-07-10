package com.csu.carenest.careadmin.common;

import java.util.List;

/**
 * 文档固定分页结构，字段必须为 records、total、page、size。
 */
public record PageData<T>(List<T> records, long total, int page, int size) {
}
