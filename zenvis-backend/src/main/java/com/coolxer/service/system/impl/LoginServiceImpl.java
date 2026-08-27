package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.RolePermission;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.entity.UserRole;
import com.coolxer.dao.mysql.repository.*;
import com.coolxer.model.system.dto.LoginDto;
import com.coolxer.model.system.vo.LoginVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.model.system.vo.UserVo;
import com.coolxer.service.system.CryptService;
import com.coolxer.service.system.LoginService;
import com.coolxer.service.system.PermissionTreeService;
import com.coolxer.utils.BCrypt;
import com.coolxer.utils.RsaUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 登录接口实现类
 */
@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

    /**
     * 用户session map 初始化容量
     */
    private static final int INITIAL_CAPACITY = 4;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PermissionTreeService permissionTreeService;

    @Autowired
    public UserRoleRepository userRoleRepository;

    @Autowired
    public RoleRepository roleRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    public CryptService cryptService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Override
    public void checkAuthCode(String authCode, HttpServletRequest request) {
        //验证码校验
        String strSession = request.getRequestedSessionId();
        String codeKey = authcodeKey(strSession);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(codeKey))) {
            if (!authCode.equalsIgnoreCase(stringRedisTemplate.opsForValue().get(codeKey))) {
                // 验证码验证一次后就失效
                stringRedisTemplate.delete(codeKey);
                throw new ApiException(ResultCodeEnum.ERROR_CODE);
            } else {
                // 验证码验证一次后就失效
                stringRedisTemplate.delete(codeKey);
            }
        } else {
            throw new ApiException(ResultCodeEnum.ERROR_CODE_INVALID);
        }
    }


    @Override
    public User checkUserAndPasswd(LoginDto loginDto) {

        String password = cryptService.decryptByRsaPrivateKey(loginDto.getPassword().trim(),
                RsaUtils.PRI_KY);
        User user = userRepository.findByEmail(loginDto.getUserName());
        if (Objects.isNull(user)) {
            throw new ApiException(ResultCodeEnum.ERROR_EMAIL_OR_PASSWORD);
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ApiException(ResultCodeEnum.ERROR_EMAIL_OR_PASSWORD);
        }
        return user;
    }

    @Override
    public void saveSessionToRedis(User user, HttpServletRequest request) {
        Map<String, String> mapSession = new HashMap<>(INITIAL_CAPACITY);

        mapSession.put("strUid", user.getId().toString());
        mapSession.put("strUname", user.getName());
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(request.getRequestedSessionId()))) {
            HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
            mapSession.forEach((k, v) -> hashOperations.put(request.getSession().getId(), k, v));
            stringRedisTemplate.expire(request.getSession().getId(),
                    Duration.ofSeconds(customWebConfig.getSessionTimeout().getSeconds()));
        }
    }

    @Override
    public LoginVo returnData(User user, HttpServletRequest request) {

        // 把会话信息保存到Redis中
        saveSessionToRedis(user, request);

        // 返回给前端的用户信息
        return buildUserInfo(user);
    }

    @Override
    public LoginVo buildUserInfo(User user) {

        // 权限菜单
        UserRole userRole = userRoleRepository.findByUserId(user.getId());

        List<RolePermission> rolePermissionList = rolePermissionRepository.findByRoleId(
                userRole.getRoleId());

        List<Integer> permissionIdList = rolePermissionList.stream()
                .map(RolePermission::getPermissionId).toList();

        LoginVo loginVo = new LoginVo();
        loginVo.setPermission(permissionTreeService.getPermissionTreeFromMenu(
                menuRepository.findByIdIn(permissionIdList).stream().map(MenuVo::new).toList()));

        Role role = roleRepository.findById(userRole.getRoleId());
        loginVo.setUser(
                new UserVo(user.getId(), user.getEmail(), user.getName(), userRole.getRoleId(),
                        role.getName(), user.getUpdateTime()));

        return loginVo;
    }

    /**
     * 验证码key
     */
    private String authcodeKey(String strSession) {
        return strSession + "strCode";
    }


}
