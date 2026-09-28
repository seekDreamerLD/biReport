package com.bireport.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(Result.CODE_ERROR, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
