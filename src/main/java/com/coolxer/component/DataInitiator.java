package com.coolxer.component;

import com.coolxer.commons.constant.SystemBuiltInConstants;
import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.dao.mysql.entity.*;
import com.coolxer.dao.mysql.repository.*;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.service.system.SystemInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统数据初始化
 */
@Slf4j
@Service
public class DataInitiator {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private PushTaskService pushTaskService;

    @Autowired
    private SystemInfoRepository systemInfoRepository;

    @Autowired
    private ResourceLoader resourceLoader;


    public void initData() {

        // 用户自定义看板
        initDefaultDashboard();

        // 初始化菜单权限
        initDefaultPermission();

        // 更新内置菜单名称
        updateBuiltInMenuNames();

        // 初始化超级管理员账号
        initDefaultSuperAdminUser();

        // 初始化管理员账号
        initDefaultAdminUser();

        // 初始化系统信息
        initDefaultSystemInfo();

    }

    /**
     * 初始化默认看板
     */
    private void initDefaultDashboard() {
        if (CollectionUtils.isEmpty(dashboardRepository.findAll())) {
            // 需要初始化
            ArrayList<Dashboard> dashboardArrayList = new ArrayList<>();
            dashboardArrayList.add(new Dashboard().setName("系统总览").setCode("msg-board").setType(DashboardType.BUILT).setUrl(""));
            dashboardArrayList.add(new Dashboard().setName("外链接视图-测试").setCode("link-test-baidu").setType(DashboardType.LINK).setUrl("https://www.baidu.com"));
            dashboardRepository.saveAll(dashboardArrayList);

        }
    }

    /**
     * 初始化默认权限列表
     */
    private void initDefaultPermission() {
        if (CollectionUtils.isEmpty(menuRepository.findAll())) {
            // 需要初始化
            Menu dashboardMenu = menuRepository.save(new Menu().setName("大屏看板").setType(MenuType.BUILT_APP).setRoute("dashboard").setIsEditable(false).setParentId(0).setOrderNumber(1).setLevel(MenuLevel.LEVEL_1));

            Menu retrievalMenu = menuRepository.save(new Menu().setName("全局检索").setType(MenuType.BUILT_APP).setRoute("retrieval").setIsEditable(false).setParentId(0).setOrderNumber(2).setLevel(MenuLevel.LEVEL_1));

            Menu policyMenu = menuRepository.save(new Menu().setName("配置管理").setType(MenuType.BUILT_APP).setRoute("policy").setIsEditable(false).setParentId(0).setOrderNumber(3).setLevel(MenuLevel.LEVEL_1));
            menuRepository.save(new Menu().setName("元数据配置").setType(MenuType.POLICY_CONFIG).setRoute(MenuType.POLICY_CONFIG.getRoute()).setParams("meta").setIsEditable(false).setParentId(policyMenu.getId()).setOrderNumber(1).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("静态页面配置").setType(MenuType.POLICY_CONFIG).setRoute(MenuType.POLICY_CONFIG.getRoute()).setParams("html-page").setIsEditable(false).setParentId(policyMenu.getId()).setOrderNumber(2).setLevel(MenuLevel.LEVEL_2));

            Menu serviceMenu = menuRepository.save(new Menu().setName("服务管理").setType(MenuType.BUILT_APP).setRoute("system").setIsEditable(false).setParentId(0).setOrderNumber(4).setLevel(MenuLevel.LEVEL_1));
            menuRepository.save(new Menu().setName("数据推送服务").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("push-task").setIsEditable(false).setParentId(serviceMenu.getId()).setOrderNumber(1).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("AI分析任务").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("analysis-task").setIsEditable(false).setParentId(serviceMenu.getId()).setOrderNumber(2).setLevel(MenuLevel.LEVEL_2));

            Menu systemMenu = menuRepository.save(new Menu().setName("系统管理").setType(MenuType.BUILT_APP).setRoute("system").setIsEditable(false).setParentId(0).setOrderNumber(5).setLevel(MenuLevel.LEVEL_1));
            menuRepository.save(new Menu().setName("菜单管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("menu").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(1).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("看板管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("dashboard").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(2).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("插件管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("plugin").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(3).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("Skill 管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("skill").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(4).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("MCP 服务").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("mcp").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(5).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("RAG 文档管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("vectorstore").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(6).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("用户管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("user").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(7).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("角色管理").setType(MenuType.LOW_CODE_PAGE).setRoute(MenuType.LOW_CODE_PAGE.getRoute()).setParams("role").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(8).setLevel(MenuLevel.LEVEL_2));
            menuRepository.save(new Menu().setName("关于产品").setType(MenuType.BUILT_APP).setRoute("system-about").setIsEditable(false).setParentId(systemMenu.getId()).setOrderNumber(9).setLevel(MenuLevel.LEVEL_2));

        }


    }

    /**
     * 更新已有环境中的内置菜单名称。
     */
    private void updateBuiltInMenuNames() {
        List<Menu> updatedMenus = menuRepository.findAll().stream()
                .filter(menu -> Boolean.FALSE.equals(menu.getIsEditable()))
                .filter(menu -> MenuType.LOW_CODE_PAGE == menu.getType())
                .filter(this::updateBuiltInMenuName)
                .toList();
        if (CollectionUtils.isNotEmpty(updatedMenus)) {
            menuRepository.saveAll(updatedMenus);
            log.info("已更新 {} 个内置菜单名称", updatedMenus.size());
        }
    }

    private boolean updateBuiltInMenuName(Menu menu) {
        if ("push-task".equals(menu.getParams()) && "数推服务".equals(menu.getName())) {
            menu.setName("数据推送服务");
            return true;
        }
        if ("analysis-task".equals(menu.getParams()) && "分析任务".equals(menu.getName())) {
            menu.setName("AI分析任务");
            return true;
        }
        return false;
    }

    /**
     * 默认创建机构管理员
     */
    private void initDefaultAdminUser() {
        String defaultAdminEmail = "admin@admin.com";
        String email = defaultAdminEmail;
        User user = userRepository.findByEmail(email);
        if (user == null) {
            // 初始化机构管理员角色
            String adminRole = "机构管理员";
            // admin@!QAZ2wsx
            String defaultAdminPassword = SystemBuiltInConstants.DEFAULT_ADMIN_PASSWORD;

            Role role = new Role();
            role.setName(adminRole);
            role = roleRepository.save(role);

            // 初始化机构管理员用户
            user = new User();
            user.setEmail(email);
            user.setName(adminRole);
            user.setPassword(defaultAdminPassword);
            user = userRepository.save(user);

            // 初始化机构管理员用户角色
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);

            // 权限菜单
            List<Menu> menuList = menuRepository.findAll();

            Role finalRole = role;
            List<RolePermission> rolePermissionList = menuList.stream().map(p -> {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(finalRole.getId());
                rolePermission.setPermissionId(p.getId());
                return rolePermission;
            }).toList();

            rolePermissionRepository.saveAll(rolePermissionList);

        }

    }

    /**
     * 默认创建超级管理员
     */
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultSuperAdminUser() {
        Role role = findOrCreateSuperAdminRole();
        User user = findOrCreateSuperAdminUser();
        UserRole userRole = userRoleRepository.findByUserId(user.getId());
        if (userRole == null) {
            userRole = new UserRole();
            userRole.setUserId(user.getId());
        }
        if (userRole.getRoleId() != role.getId()) {
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
        }
        syncSuperAdminRolePermissions(role.getId());
    }

    private Role findOrCreateSuperAdminRole() {
        List<Role> namedRoles = roleRepository.findByName(SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME);
        List<Role> roles = roleRepository.findByIsSuperAdmin(true);
        Role role = CollectionUtils.isEmpty(namedRoles)
                ? (CollectionUtils.isEmpty(roles) ? null : roles.get(0))
                : namedRoles.get(0);
        if (role == null) {
            role = new Role();
            role.setName(SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME);
            role.setIsSuperAdmin(true);
            return roleRepository.save(role);
        }
        boolean needSave = false;
        if (!SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME.equals(role.getName())) {
            role.setName(SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME);
            needSave = true;
        }
        if (!Boolean.TRUE.equals(role.getIsSuperAdmin())) {
            role.setIsSuperAdmin(true);
            needSave = true;
        }
        return needSave ? roleRepository.save(role) : role;
    }

    private User findOrCreateSuperAdminUser() {
        User user = userRepository.findByEmail(SystemBuiltInConstants.SUPER_ADMIN_EMAIL);
        List<User> users = userRepository.findByIsSuperAdmin(true);
        if (user == null && CollectionUtils.isNotEmpty(users)) {
            user = users.get(0);
        }
        if (user == null) {
            user = new User();
            user.setEmail(SystemBuiltInConstants.SUPER_ADMIN_EMAIL);
            user.setName(SystemBuiltInConstants.SUPER_ADMIN_NAME);
            user.setPassword(SystemBuiltInConstants.DEFAULT_ADMIN_PASSWORD);
            user.setIsSuperAdmin(true);
            return userRepository.save(user);
        }
        boolean needSave = false;
        if (!SystemBuiltInConstants.SUPER_ADMIN_EMAIL.equals(user.getEmail())) {
            user.setEmail(SystemBuiltInConstants.SUPER_ADMIN_EMAIL);
            needSave = true;
        }
        if (!SystemBuiltInConstants.SUPER_ADMIN_NAME.equals(user.getName())) {
            user.setName(SystemBuiltInConstants.SUPER_ADMIN_NAME);
            needSave = true;
        }
        if (!Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            user.setIsSuperAdmin(true);
            needSave = true;
        }
        return needSave ? userRepository.save(user) : user;
    }

    private void syncSuperAdminRolePermissions(Integer roleId) {
        List<Menu> menuList = menuRepository.findAll();
        Set<Integer> existingPermissionIds = new HashSet<>(rolePermissionRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermissionId)
                .toList());
        List<RolePermission> missingPermissions = menuList.stream()
                .filter(menu -> !existingPermissionIds.contains(menu.getId()))
                .map(menu -> new RolePermission(roleId, menu.getId()))
                .toList();
        rolePermissionRepository.saveAll(missingPermissions);
    }

    /**
     * 初始化默认系统信息
     */
    private void initDefaultSystemInfo() {
        if (systemInfoRepository.findById(1).isEmpty()) {
            // 需要初始化默认系统信息
            SystemInfo systemInfo = new SystemInfo();
            systemInfo.setId(1);
            systemInfo.setSystemIcon("/system-files/" + SystemInfoService.SYSTEM_ICON_FILENAME);
            systemInfo.setSystemLogo("/system-files/" + SystemInfoService.SYSTEM_LOGO_FILENAME);
            systemInfo.setSystemBanner("/system-files/" + SystemInfoService.SYSTEM_BANNER_FILENAME);
            systemInfo.setSystemTitle("ZenVis");
            systemInfo.setProductName("ZenVis — 数据分析应用框架");
            systemInfo.setProductVersion("1.0.0.alpha");
            systemInfo.setProductIntroduction("ZenVis\n一个基于配置实现的数据存储、可视化及业务扩展的框架平台，实现在通用的数据分析框架之上构建业务应用。提供智能分析能力，全方位满足数据处理、展示、扩展与深度分析需求。");
            systemInfo.setServicePhone("待补充");
            systemInfo.setServiceEmail("coolxer@163.com");
            systemInfo.setTechnicalEmail("coolxer@163.com");
            systemInfo.setIntegrateLink("https://coolxer.com");
            systemInfo.setCopyright("Copyright  2026 coolXer社区团队. All rights reserved.");
            systemInfoRepository.save(systemInfo);
            log.info("初始化默认系统信息完成");
        }
    }

}
