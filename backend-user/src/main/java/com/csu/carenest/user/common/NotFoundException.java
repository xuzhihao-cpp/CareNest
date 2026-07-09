package com.csu.carenest.user.common;

public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(404, "不存在");
    }
}
