package com.liucc.passage.mapstruct;

import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MUserMapper {
    MUserMapper INSTANCE = Mappers.getMapper(MUserMapper.class);

    UserVO entity2VO(User user);
}
