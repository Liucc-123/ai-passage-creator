package com.liucc.passage.enums;

import lombok.Getter;

@Getter
public enum UserEnum {

    USER("user", "普通用户"),
    ADMIN("admin", "管理员")
    ;

    UserEnum(String code, String text) {
        this.code = code;
        this.text = text;
    }

    private final String code;
    private final String text;
}
