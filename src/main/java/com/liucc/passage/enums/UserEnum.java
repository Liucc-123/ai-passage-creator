package com.liucc.passage.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserEnum {

    USER("user", "普通用户"),
    ADMIN("admin", "管理员");

    private final String code;
    private final String text;
}
