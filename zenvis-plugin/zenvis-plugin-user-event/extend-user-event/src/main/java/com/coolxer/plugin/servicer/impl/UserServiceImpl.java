package com.coolxer.plugin.servicer.impl;

import com.coolxer.plugin.controller.PageRowsVo;
import com.coolxer.plugin.controller.UserDto;
import com.coolxer.plugin.controller.UserSearchDto;
import com.coolxer.plugin.controller.UserVo;
import com.coolxer.plugin.servicer.UserService;
import com.coolxer.plugin.util.CardGenerate;
import com.coolxer.plugin.util.NameGenerate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Override
    public boolean add(UserDto userDto) {
        // 假设添加成功
        return true;
    }

    @Override
    public PageRowsVo<UserVo> getPageList(UserSearchDto userSearchDto) {
        String id = userSearchDto.getId();
        if(id == null){
            id = "id";
        }
        int page = userSearchDto.getPage();
        int perPage = userSearchDto.getPerPage();
        // 构造假数据
        List<UserVo> userVoList = new ArrayList<>();
        for (int i = 0; i < perPage; i++) {
            userVoList.add(new UserVo(id+i, new NameGenerate().name(), CardGenerate.card()));
        }
        return new PageRowsVo<>(userVoList, 3);
    }
}
