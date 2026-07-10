package com.csu.carenest.user.common;

public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(403, "无权限");
    }
}
