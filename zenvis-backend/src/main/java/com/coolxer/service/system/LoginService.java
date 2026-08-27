package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.system.dto.LoginDto;
import com.coolxer.model.system.vo.LoginVo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录接口
 */
public interface LoginService {


    /**
     * 校验验证码
     *
     * @param authCode 验证码
     * @param request  请求
     */
    void checkAuthCode(String authCode, HttpServletRequest request);


    /**
     * 检验登录名、密码
     *
     * @param loginDto 用户对象
     * @return 验证结果
     */
    User checkUserAndPasswd(LoginDto loginDto);


    /**
     * 把会话信息保存到Redis中。
     *
     * @param user    用户对象
     * @param request HTTP请求对象
     */
    void saveSessionToRedis(User user, HttpServletRequest request);

    /**
     * 组织登录成功返回值
     *
     * @param user    用户对象
     * @param request 请求
     * @return 结果
     */
    LoginVo returnData(User user, HttpServletRequest request);


    /**
     * 构建返回给前端的用户信息
     *
     * @param user 用户对象
     * @return 返回给前端用户信息
     */
    LoginVo buildUserInfo(User user);

}
