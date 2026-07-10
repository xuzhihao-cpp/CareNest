package com.csu.carenest.careadmin.common;

/**
 * 403 无权限。
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(403, "forbidden");
    }
}
