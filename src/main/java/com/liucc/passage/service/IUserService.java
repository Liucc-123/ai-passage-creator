package com.liucc.passage.service;

import com.liucc.passage.model.UserVO;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.model.entity.User;
import com.mybatisflex.core.service.IService;

public interface IUserService extends IService<User> {

    /**
     * 注册用户
     * @param request
     */
    UserVO register(UserRegisterRequest request);
}
