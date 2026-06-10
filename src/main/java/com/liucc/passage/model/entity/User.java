package com.liucc.passage.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Table(value = "user", camelToUnderline = false)
@Data
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String userAccount;       // 登录账号，唯一索引
    private String userPassword;      // MD5 + 盐加密，格式："salt$md5(salt+password)"
    private String userName;          // 展示用户名，可选（默认同账号）
    private String userAvatar;        // 头像 URL
    private String userProfile;       // 简介
    private String userRole;          // 角色："user" / "admin"
    private Date vipTime;             // VIP 过期时间，null 表示非 VIP
    private Integer quota;            // 剩余创作配额，默认 5
    private Date editTime;            // 更新时间
    private Date createTime;          // 创建时间
    private Date updateTime;          // 更新时间
    @Column(isLogicDelete = true)
    private Integer isDelete;         // 逻辑删除，0=未删除，1=已删除
}
