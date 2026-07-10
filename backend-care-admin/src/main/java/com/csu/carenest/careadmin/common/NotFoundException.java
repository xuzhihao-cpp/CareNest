package com.csu.carenest.careadmin.common;

/**
 * 404 数据不存在。
 */
public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(404, "not found");
    }
}
