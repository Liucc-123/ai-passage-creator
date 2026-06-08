package com.liucc.passage.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    NOT_AUTH_ERROR(40101, "未授权错误")
    ;


    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
