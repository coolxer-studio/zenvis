package com.coolxer.service.system.impl;

import com.coolxer.commons.constant.SystemBuiltInConstants;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.entity.UserRole;
import com.coolxer.dao.mysql.repository.RoleRepository;
import com.coolxer.dao.mysql.repository.UserRepository;
import com.coolxer.dao.mysql.repository.UserRoleRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.PasswordChangeDto;
import com.coolxer.model.system.dto.UserDto;
import com.coolxer.model.system.dto.UserSearchDto;
import com.coolxer.model.system.vo.UserVo;
import com.coolxer.service.system.CryptService;
import com.coolxer.service.system.UserService;
import com.coolxer.utils.BCrypt;
import com.coolxer.utils.RsaUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /**
     * 密码验证匹配次数
     */
    private static final int MATCHER_TIMES = 4;

    /**
     * 密码最小长度限制
     */
    private static final int PASSWORD_MIN_LENGTH_LIMIT = 8;

    /**
     * 密码最大长度限制
     */
    private static final int PASSWORD_MAX_LENGTH_LIMIT = 32;

    /**
     * 数字匹配.
     */
    private Pattern numRegEx = Pattern.compile("\\d");

    /**
     * 小写字母匹配.
     */
    private Pattern lowerRegEx = Pattern.compile("[a-z]");

    /**
     * 大写字母匹配.
     */
    private Pattern upperRegEx = Pattern.compile("[A-Z]");

    /**
     * 特殊字符匹配.
     */
    private Pattern specialRegEx = Pattern.compile("[!@#$]");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    public HttpServletRequest request;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;


    @Autowired
    public CryptService cryptService;

    @Override
    public PageRowsVo<UserVo> getPageList(UserSearchDto userSearchDto, User currentUser) {

        try {
            Pageable pageable = PageRequest.of(userSearchDto.getPage() - 1, userSearchDto.getPerPage());
            Page<User> byPage;
            if (SystemBuiltInConstants.isSuperAdmin(currentUser)) {
                byPage = userRepository.findByPage(pageable, userSearchDto.getName(), userSearchDto.getEmail());
            } else {
                byPage = userRepository.findByPageWithoutSuperAdmin(pageable, userSearchDto.getName(), userSearchDto.getEmail());
            }

            // 用户id列表
            List<Integer> userIdList = byPage.stream().map(User::getId).toList();

            List<UserRole> userRoleList = userRoleRepository.findByUserIdIn(userIdList);
            // 角色列表
            List<Integer> roleIdList = userRoleList.stream().map(UserRole::getRoleId).toList();

            List<Role> roleList = roleRepository.findByIdIn(roleIdList);

            // 用户id作为key，用户角色作为value
            Map<Integer, UserRole> userRoleMap = userRoleList.stream()
                    .collect(Collectors.toMap(UserRole::getUserId, a -> a, (k1, k2) -> k1));

            // 角色id作为key，角色名称作为value
            Map<Integer, String> roleMap = roleList.stream()
                    .collect(Collectors.toMap(Role::getId, Role::getName));

            return new PageRowsVo<>(
                    byPage.getContent().stream().map(user -> {
                        UserRole userRole = userRoleMap.get(user.getId());
                        Integer roleId = userRole.getRoleId();
                        return new UserVo(user.getId(), user.getEmail(), user.getName(), roleId,
                                roleMap.get(roleId), user.getUpdateTime(), user.getIsSuperAdmin());
                    }).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public User create(UserDto userDto) {
        // 必填项校验
        checkCreate(userDto);

        // 邮箱校验，系统唯一
        User existUser = userRepository.findByEmail(userDto.getEmail());
        if (Objects.nonNull(existUser)) {
            throw new ApiException(ResultCodeEnum.EMAIL_IS_EXIST);
        }
        Role role = roleRepository.findById(userDto.getRoleId());
        if (SystemBuiltInConstants.isSuperAdmin(role)) {
            throw new ApiException(ResultCodeEnum.SUPER_ADMIN_ROLE_ASSIGN_NOT_ALLOWED);
        }

        String password = userDto.getPassword();

        // 校验密码格式
        if (passwordCheckFail(password)) {
            throw new ApiException(ResultCodeEnum.ERROR_PASSWORD_FORMAT);
        }

        // 密码加密
        User userCreated =
                userRepository.save(
                        new User(userDto.getEmail(), userDto.getName(),
                                BCrypt.hashpw(password, BCrypt.GENSALT_DEFAULT)));

        // 分配角色
        UserRole userRole = new UserRole();
        userRole.setUserId(userCreated.getId());
        userRole.setRoleId(userDto.getRoleId());

        userRoleRepository.save(userRole);

        return userCreated;
    }

    private static void checkCreate(UserDto userDto) {
        // 必填项校验
        checkCreateOrUpdate(userDto);

        if (StringUtils.isEmpty(userDto.getPassword())) {
            throw new ApiException(ResultCodeEnum.PASSWORD_MUST_NOT_NULL);
        }

    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public Boolean update(Long id, UserDto userDto) {
        // 必填项校验
        checkCreateOrUpdate(userDto);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (SystemBuiltInConstants.isSuperAdmin(user)) {
                throw new ApiException(ResultCodeEnum.SUPER_ADMIN_USER_NOT_ALLOWED);
            }
            Role role = roleRepository.findById(userDto.getRoleId());
            if (SystemBuiltInConstants.isSuperAdmin(role)) {
                throw new ApiException(ResultCodeEnum.SUPER_ADMIN_ROLE_ASSIGN_NOT_ALLOWED);
            }

            // 修改邮箱，校验邮箱，系统唯一
            User existUser = userRepository.findByEmail(userDto.getEmail());
            if (Objects.nonNull(existUser) && !Objects.equals(existUser.getId(), user.getId())) {
                throw new ApiException(ResultCodeEnum.EMAIL_IS_EXIST);
            }

            // 如果修改密码
            String newPassword = userDto.getPassword();
            if (StringUtils.isNotEmpty(newPassword)) {
                // 校验密码格式
                if (passwordCheckFail(newPassword)) {
                    throw new ApiException(ResultCodeEnum.ERROR_PASSWORD_FORMAT);
                }

                // 验证新旧密码是否相同，如果新旧密码相同，则提示
                if (BCrypt.checkpw(newPassword, user.getPassword())) {
                    throw new ApiException(ResultCodeEnum.ERROR_NEW_PASSWORD_IS_SAME_AS_OLD);
                }
                user.setPassword(BCrypt.hashpw(newPassword, BCrypt.GENSALT_DEFAULT));
            }

            // 修改email
            user.setEmail(userDto.getEmail());
            // 修改name
            user.setName(userDto.getName());

            userRepository.save(user);
            UserRole userRole = userRoleRepository.findByUserId(user.getId());
            if (Objects.isNull(userRole)) {
                userRole = new UserRole();
                userRole.setUserId(user.getId());
            }
            userRole.setRoleId(userDto.getRoleId());
            userRoleRepository.save(userRole);
            return true;
        } else {
            throw new ApiException(ResultCodeEnum.ERROR_USER_IS_NOT_EXIST);
        }
    }

    @Override
    public void checkPassword(PasswordChangeDto passwordChangeDto, User user) {

        if (StringUtils.isEmpty(passwordChangeDto.getOldPassword())) {
            throw new ApiException(ResultCodeEnum.OLD_PASSWORD_MUST_NOT_NULL);
        }
        if (StringUtils.isEmpty(passwordChangeDto.getPassword())) {
            throw new ApiException(ResultCodeEnum.PASSWORD_MUST_NOT_NULL);
        }

        // 解码前端加密传输的密码
        String newPassword = cryptService.decryptByRsaPrivateKey(passwordChangeDto.getPassword(),
                RsaUtils.PRI_KY);
        String oldPassword = cryptService.decryptByRsaPrivateKey(passwordChangeDto.getOldPassword(),
                RsaUtils.PRI_KY);

        // 校验密码格式
        if (passwordCheckFail(newPassword)) {
            throw new ApiException(ResultCodeEnum.ERROR_PASSWORD_FORMAT);
        }

        // 验证原密码
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new ApiException(ResultCodeEnum.ERROR_PASSWORD);
        }

        // 验证新旧密码是否相同，如果新旧密码相同，则提示
        if (StringUtils.equals(newPassword, oldPassword)) {
            throw new ApiException(ResultCodeEnum.ERROR_NEW_PASSWORD_IS_SAME_AS_OLD);
        }

    }

    @Override
    public void updatePassword(UserDto userDto) {

        User user = userRepository.findById(userDto.getId());
        if (Objects.isNull(user)) {
            throw new ApiException(ResultCodeEnum.ERROR_USER_IS_NOT_EXIST);
        }

        user.setPassword(BCrypt.hashpw(cryptService.decryptByRsaPrivateKey(userDto.getPassword(),
                RsaUtils.PRI_KY), BCrypt.GENSALT_DEFAULT));

        userRepository.save(user);
    }

    private static void checkCreateOrUpdate(UserDto userDto) {
        if (StringUtils.isEmpty(userDto.getEmail())) {
            throw new ApiException(ResultCodeEnum.EMAIL_MUST_NOT_NULL);
        }
        if (StringUtils.isEmpty(userDto.getName())) {
            throw new ApiException(ResultCodeEnum.USER_NAME_MUST_NOT_NULL);
        }
        if (Objects.isNull(userDto.getRoleId())) {
            throw new ApiException(ResultCodeEnum.ROLE_NAME_MUST_NOT_NULL);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public void delete(Long id) {
        if (Objects.isNull(id)) {
            throw new ApiException(ResultCodeEnum.ERROR_USER_ID_MUST_NOT_NULL);
        }
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent() && SystemBuiltInConstants.isSuperAdmin(optionalUser.get())) {
            throw new ApiException(ResultCodeEnum.SUPER_ADMIN_USER_NOT_ALLOWED);
        }
        userRepository.deleteById(id);
        userRoleRepository.deleteByUserId(id.intValue());

    }

    @Override
    public UserVo info(Long id, User currentUser) {
        try {
            Optional<User> optionalUser = userRepository.findById(id);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                if (SystemBuiltInConstants.isSuperAdmin(user) && !SystemBuiltInConstants.isSuperAdmin(currentUser)) {
                    return null;
                }
                UserRole userRole = userRoleRepository.findByUserId(id.intValue());
                Role role = roleRepository.findById(userRole.getRoleId());
                return new UserVo(user, role);
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    /**
     * 密码格式检查，密码至少包含数字 大写字母 小写字母 特殊字符中的两种，且长度在8-32位之间.
     */
    private boolean passwordCheckFail(String strPassword) {
        if (StringUtils.isBlank(strPassword)) {
            return true;
        }

        String password = strPassword.trim();
        int num = 0;
        //数字匹配
        num = numRegEx.matcher(password).find() ? num + 1 : num;
        //小写字母匹配
        num = lowerRegEx.matcher(password).find() ? num + 1 : num;
        //大写字母匹配
        num = upperRegEx.matcher(password).find() ? num + 1 : num;
        //特殊字符匹配
        num = specialRegEx.matcher(password).find() ? num + 1 : num;
        return num < MATCHER_TIMES || password.length() < PASSWORD_MIN_LENGTH_LIMIT
                || password.length() > PASSWORD_MAX_LENGTH_LIMIT;
    }

}
