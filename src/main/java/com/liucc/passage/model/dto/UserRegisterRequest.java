package com.liucc.passage.model.dto;

import lombok.Data;

/**
 * 用户注册请求对象
 */
@Data
public class UserRegisterRequest {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String confirmPassword;
}
