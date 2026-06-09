package com.liucc.passage.controller;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSONObject;
import com.liucc.passage.common.BaseResponse;
import com.liucc.passage.model.UserVO;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ResponseBody
@RequestMapping("/user")
@Slf4j
public class UserController {

    @PostMapping("/register")
    public BaseResponse<UserVO> register(UserRegisterRequest request) {
        log.info("request: {}", JSONObject.toJSONString(request));
        IUserService userService = SpringUtil.getBean(IUserService.class);
        UserVO userVO = userService.register(request);
        return BaseResponse.success(userVO);
    }

}
