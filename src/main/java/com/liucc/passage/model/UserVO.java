package com.liucc.passage.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author liucc
 * @date 2026 年 6 月 9 日
 * @description 用户VO对象，去除敏感信息
 */
@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userAccount;   // 登录账号（可显示）
    private String userName;      // 展示用户名
    private String userAvatar;    // 头像 URL
    private String userProfile;   // 简介
    private String userRole;      // 角色
    private Integer quota;        // 剩余配额
    private Integer vipType;      // VIP 类型：0=普通，1=VIP
}