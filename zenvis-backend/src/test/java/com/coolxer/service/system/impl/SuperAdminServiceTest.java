package com.coolxer.service.system.impl;

import com.coolxer.commons.constant.SystemBuiltInConstants;
import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.component.DataInitiator;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Role;
import com.coolxer.dao.mysql.entity.RolePermission;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.entity.UserRole;
import com.coolxer.dao.mysql.repository.MenuRepository;
import com.coolxer.dao.mysql.repository.RolePermissionRepository;
import com.coolxer.dao.mysql.repository.RoleRepository;
import com.coolxer.dao.mysql.repository.UserRepository;
import com.coolxer.dao.mysql.repository.UserRoleRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.RoleDto;
import com.coolxer.model.system.dto.RoleSearchDto;
import com.coolxer.model.system.dto.UserDto;
import com.coolxer.model.system.dto.UserSearchDto;
import com.coolxer.model.system.vo.RoleVo;
import com.coolxer.model.system.vo.UserVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.CryptService;
import com.coolxer.utils.BCrypt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private ConfigService configService;

    @Mock
    private CryptService cryptService;

    private UserServiceImpl userService;

    private RoleServiceImpl roleService;

    private MenuServiceImpl menuService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "userRoleRepository", userRoleRepository);
        ReflectionTestUtils.setField(userService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(userService, "cryptService", cryptService);

        roleService = new RoleServiceImpl();
        ReflectionTestUtils.setField(roleService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(roleService, "userRoleRepository", userRoleRepository);
        ReflectionTestUtils.setField(roleService, "rolePermissionRepository", rolePermissionRepository);
        ReflectionTestUtils.setField(roleService, "menuRepository", menuRepository);

        menuService = new MenuServiceImpl();
        ReflectionTestUtils.setField(menuService, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(menuService, "configService", configService);
        ReflectionTestUtils.setField(menuService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(menuService, "rolePermissionRepository", rolePermissionRepository);
    }

    @Test
    void initDefaultSuperAdminUserCreatesBuiltInUserRoleAndMissingPermissions() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "userRepository", userRepository);
        ReflectionTestUtils.setField(dataInitiator, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(dataInitiator, "userRoleRepository", userRoleRepository);
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(dataInitiator, "rolePermissionRepository", rolePermissionRepository);
        ReflectionTestUtils.setField(dataInitiator, "bootstrapSuperAdminPassword", "super-admin-test-password");

        when(roleRepository.findByIsSuperAdmin(true)).thenReturn(List.of());
        when(roleRepository.findByName(SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME)).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(10);
            return role;
        });
        when(userRepository.findByIsSuperAdmin(true)).thenReturn(List.of());
        when(userRepository.findByEmail(SystemBuiltInConstants.SUPER_ADMIN_EMAIL)).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(20);
            return user;
        });
        when(userRoleRepository.findByUserId(20)).thenReturn(null);
        when(menuRepository.findAll()).thenReturn(List.of(menu(1), menu(2)));
        when(rolePermissionRepository.findByRoleId(10)).thenReturn(List.of(new RolePermission(10, 1)));

        dataInitiator.initDefaultSuperAdminUser();

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getName()).isEqualTo(SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME);
        assertThat(roleCaptor.getValue().getIsSuperAdmin()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo(SystemBuiltInConstants.SUPER_ADMIN_EMAIL);
        assertThat(userCaptor.getValue().getIsSuperAdmin()).isTrue();
        assertThat(BCrypt.checkpw("super-admin-test-password", userCaptor.getValue().getPassword())).isTrue();

        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(userRoleCaptor.capture());
        assertThat(userRoleCaptor.getValue().getUserId()).isEqualTo(20);
        assertThat(userRoleCaptor.getValue().getRoleId()).isEqualTo(10);

        ArgumentCaptor<Iterable<RolePermission>> permissionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).saveAll(permissionsCaptor.capture());
        assertThat(permissionsCaptor.getValue()).extracting(RolePermission::getPermissionId).containsExactly(2);
    }

    @Test
    void initDefaultSuperAdminUserRejectsMissingBootstrapPasswordForNewDatabase() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "userRepository", userRepository);
        ReflectionTestUtils.setField(dataInitiator, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(dataInitiator, "userRoleRepository", userRoleRepository);
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(dataInitiator, "rolePermissionRepository", rolePermissionRepository);
        ReflectionTestUtils.setField(dataInitiator, "bootstrapSuperAdminPassword", "");

        when(userRepository.findByIsSuperAdmin(true)).thenReturn(List.of());
        when(userRepository.findByEmail(SystemBuiltInConstants.SUPER_ADMIN_EMAIL)).thenReturn(null);

        assertThatThrownBy(dataInitiator::initDefaultSuperAdminUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZENVIS_BOOTSTRAP_SUPER_ADMIN_PASSWORD");
    }

    @Test
    void existingBuiltInPushTaskMenuIsRenamed() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);

        Menu pushTaskMenu = builtInServiceMenu("数推服务", "push-task");
        Menu editableMenu = builtInServiceMenu("数推服务", "push-task").setIsEditable(true);
        when(menuRepository.findAll()).thenReturn(List.of(pushTaskMenu, editableMenu));

        ReflectionTestUtils.invokeMethod(dataInitiator, "updateBuiltInMenuNames");

        assertThat(pushTaskMenu.getName()).isEqualTo("数据推送服务");
        assertThat(editableMenu.getName()).isEqualTo("数推服务");

        ArgumentCaptor<Iterable<Menu>> menusCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(menuRepository).saveAll(menusCaptor.capture());
        assertThat(menusCaptor.getValue()).containsExactly(pushTaskMenu);
    }

    @Test
    void legacyBuiltInAnalysisTaskMenuAndPermissionsAreRemoved() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(dataInitiator, "rolePermissionRepository", rolePermissionRepository);

        Menu analysisTaskMenu = builtInServiceMenu("AI分析任务", "analysis-task");
        analysisTaskMenu.setId(22);
        Menu editableMenu = builtInServiceMenu("自定义分析页面", "analysis-task").setIsEditable(true);
        editableMenu.setId(23);
        RolePermission permission = new RolePermission(3, 22);
        when(menuRepository.findAll()).thenReturn(List.of(analysisTaskMenu, editableMenu));
        when(rolePermissionRepository.findByPermissionId(22)).thenReturn(List.of(permission));

        ReflectionTestUtils.invokeMethod(dataInitiator, "removeLegacyAnalysisTaskMenu");

        verify(rolePermissionRepository).deleteAll(List.of(permission));
        verify(menuRepository).delete(analysisTaskMenu);
        verify(menuRepository, never()).delete(editableMenu);
    }

    @Test
    void businessServiceMenuIsCreatedAndInheritsOnlyParentMenuRoles() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(dataInitiator, "rolePermissionRepository", rolePermissionRepository);

        Menu serviceMenu = new Menu()
                .setName("服务管理")
                .setParentId(0)
                .setIsEditable(false);
        serviceMenu.setId(4);
        when(menuRepository.findAll()).thenReturn(List.of(serviceMenu));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setId(30);
            return menu;
        });
        when(rolePermissionRepository.findByPermissionId(4))
                .thenReturn(List.of(new RolePermission(10, 4), new RolePermission(20, 4)));
        when(rolePermissionRepository.findByRoleIdAndPermissionId(10, 30))
                .thenReturn(new RolePermission(10, 30));
        when(rolePermissionRepository.findByRoleIdAndPermissionId(20, 30)).thenReturn(null);

        ReflectionTestUtils.invokeMethod(dataInitiator, "ensureBusinessServiceMenu");

        ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).save(menuCaptor.capture());
        assertThat(menuCaptor.getValue().getName()).isEqualTo("业务应用服务");
        assertThat(menuCaptor.getValue().getParams()).isEqualTo("business-service");
        assertThat(menuCaptor.getValue().getOrderNumber()).isEqualTo(2);

        ArgumentCaptor<Iterable<RolePermission>> permissionCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(rolePermissionRepository).saveAll(permissionCaptor.capture());
        assertThat(permissionCaptor.getValue())
                .extracting(RolePermission::getRoleId, RolePermission::getPermissionId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(20, 30));
    }

    @Test
    void existingBusinessServiceMenuIsNotCreatedAgain() {
        DataInitiator dataInitiator = new DataInitiator();
        ReflectionTestUtils.setField(dataInitiator, "menuRepository", menuRepository);
        ReflectionTestUtils.setField(dataInitiator, "rolePermissionRepository", rolePermissionRepository);

        Menu serviceMenu = new Menu().setName("服务管理").setParentId(0).setIsEditable(false);
        serviceMenu.setId(4);
        Menu businessServiceMenu = builtInServiceMenu("业务应用服务", "business-service")
                .setParentId(4)
                .setOrderNumber(2);
        businessServiceMenu.setId(30);
        when(menuRepository.findAll()).thenReturn(List.of(serviceMenu, businessServiceMenu));
        when(rolePermissionRepository.findByPermissionId(4)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(dataInitiator, "ensureBusinessServiceMenu");

        verify(menuRepository, never()).save(any(Menu.class));
        verify(rolePermissionRepository, never()).saveAll(any());
    }

    @Test
    void createMenuGrantsPermissionToSuperAdminRole() {
        Role superRole = role(5, SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME, true);
        when(menuRepository.getMaxOrderNumberById(0)).thenReturn(Optional.of(0));
        when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            menu.setId(7);
            return menu;
        });
        when(roleRepository.findByIsSuperAdmin(true)).thenReturn(List.of(superRole));
        when(rolePermissionRepository.findByRoleIdAndPermissionId(5, 7)).thenReturn(null);

        MenuDto dto = new MenuDto();
        dto.setName("测试菜单");
        dto.setType(MenuType.BUILT_APP);
        dto.setRoute("test");
        dto.setLevel(MenuLevel.LEVEL_1);

        menuService.create(dto);

        ArgumentCaptor<RolePermission> permissionCaptor = ArgumentCaptor.forClass(RolePermission.class);
        verify(rolePermissionRepository).save(permissionCaptor.capture());
        assertThat(permissionCaptor.getValue().getRoleId()).isEqualTo(5);
        assertThat(permissionCaptor.getValue().getPermissionId()).isEqualTo(7);
    }

    @Test
    void nonSuperAdminUserListUsesFilteredQuery() {
        User currentUser = user(99, "user@admin.com", false);
        User normalUser = user(1, "normal@admin.com", false);
        Role role = role(2, "机构管理员", false);
        UserRole userRole = new UserRole();
        userRole.setUserId(1);
        userRole.setRoleId(2);

        when(userRepository.findByPageWithoutSuperAdmin(any(Pageable.class), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(normalUser), PageRequest.of(0, 10), 1));
        when(userRoleRepository.findByUserIdIn(List.of(1))).thenReturn(List.of(userRole));
        when(roleRepository.findByIdIn(List.of(2))).thenReturn(List.of(role));

        PageRowsVo<UserVo> result = userService.getPageList(new UserSearchDto(), currentUser);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getIsSuperAdmin()).isFalse();
        verify(userRepository, never()).findByPage(any(Pageable.class), isNull(), isNull());
    }

    @Test
    void superAdminUserCannotBeUpdatedOrDeletedFromUserManagement() {
        User superUser = user(1, SystemBuiltInConstants.SUPER_ADMIN_EMAIL, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superUser));

        UserDto dto = new UserDto();
        dto.setEmail("changed@admin.com");
        dto.setName("changed");
        dto.setRoleId(2);

        assertThatThrownBy(() -> userService.update(1L, dto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.SUPER_ADMIN_USER_NOT_ALLOWED.getCode());

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.SUPER_ADMIN_USER_NOT_ALLOWED.getCode());
    }

    @Test
    void updatingUserCanKeepOwnEmailWhenRequestBodyOmitsId() {
        User existingUser = user(7, "user@admin.com", false);
        existingUser.setPassword("hashed-password");
        when(userRepository.findById(7L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail("user@admin.com")).thenReturn(existingUser);

        UserDto dto = new UserDto();
        dto.setEmail("user@admin.com");
        dto.setName("updated-name");
        dto.setRoleId(2);

        assertThat(userService.update(7L, dto)).isTrue();
        verify(userRepository).save(existingUser);
    }

    @Test
    void updatingUserChangesRoleAssociation() {
        User existingUser = user(7, "user@admin.com", false);
        UserRole userRole = new UserRole();
        userRole.setUserId(7);
        userRole.setRoleId(2);
        when(userRepository.findById(7L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail("user@admin.com")).thenReturn(existingUser);
        when(userRoleRepository.findByUserId(7)).thenReturn(userRole);

        UserDto dto = new UserDto();
        dto.setEmail("user@admin.com");
        dto.setName("updated-name");
        dto.setRoleId(3);

        assertThat(userService.update(7L, dto)).isTrue();
        assertThat(userRole.getRoleId()).isEqualTo(3);
        verify(userRoleRepository).save(userRole);
    }

    @Test
    void updatingUserRejectsEmailOwnedByAnotherUser() {
        User updatedUser = user(7, "user@admin.com", false);
        User emailOwner = user(8, "other@admin.com", false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(updatedUser));
        when(userRepository.findByEmail("other@admin.com")).thenReturn(emailOwner);

        UserDto dto = new UserDto();
        dto.setEmail("other@admin.com");
        dto.setName("updated-name");
        dto.setRoleId(2);

        assertThatThrownBy(() -> userService.update(7L, dto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.EMAIL_IS_EXIST.getCode());
        verify(userRepository, never()).save(updatedUser);
    }

    @Test
    void creatingUserHashesPlaintextPasswordSubmittedByUserManagement() {
        Role normalRole = role(2, "机构管理员", false);
        when(userRepository.findByEmail("new-user@admin.com")).thenReturn(null);
        when(roleRepository.findById(2)).thenReturn(normalRole);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9);
            return user;
        });

        UserDto dto = new UserDto();
        dto.setEmail("new-user@admin.com");
        dto.setName("new-user");
        dto.setPassword("Admin@123");
        dto.setRoleId(2);

        userService.create(dto);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(BCrypt.checkpw("Admin@123", userCaptor.getValue().getPassword())).isTrue();
        verify(cryptService, never()).decryptByRsaPrivateKey(any(), any());
    }

    @Test
    void updatingAffectedUserReplacesEmptyPasswordHashWithSubmittedPassword() {
        User existingUser = user(7, "user@admin.com", false);
        existingUser.setPassword(BCrypt.hashpw("", BCrypt.GENSALT_DEFAULT));
        when(userRepository.findById(7L)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail("user@admin.com")).thenReturn(existingUser);

        UserDto dto = new UserDto();
        dto.setEmail("user@admin.com");
        dto.setName("updated-name");
        dto.setPassword("NewAdmin@123");
        dto.setRoleId(2);

        assertThat(userService.update(7L, dto)).isTrue();
        assertThat(BCrypt.checkpw("NewAdmin@123", existingUser.getPassword())).isTrue();
        assertThat(BCrypt.checkpw("", existingUser.getPassword())).isFalse();
        verify(cryptService, never()).decryptByRsaPrivateKey(any(), any());
    }

    @Test
    void cannotAssignSuperAdminRoleWhenCreatingUser() {
        Role superRole = role(9, SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME, true);
        when(userRepository.findByEmail("normal@admin.com")).thenReturn(null);
        when(roleRepository.findById(9)).thenReturn(superRole);

        UserDto dto = new UserDto();
        dto.setEmail("normal@admin.com");
        dto.setName("normal");
        dto.setPassword("encrypted");
        dto.setRoleId(9);

        assertThatThrownBy(() -> userService.create(dto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.SUPER_ADMIN_ROLE_ASSIGN_NOT_ALLOWED.getCode());
    }

    @Test
    void roleVisibilityAndProtectionHonorsSuperAdminFlag() {
        User currentUser = user(99, "user@admin.com", false);
        Role superRole = role(1, SystemBuiltInConstants.SUPER_ADMIN_ROLE_NAME, true);
        Role normalRole = role(2, "机构管理员", false);
        RoleSearchDto searchDto = new RoleSearchDto();

        when(roleRepository.findByPageWithoutSuperAdmin(any(Pageable.class), isNull()))
                .thenReturn(new PageImpl<>(List.of(normalRole), PageRequest.of(0, 10), 1));
        when(rolePermissionRepository.findByRoleId(2)).thenReturn(List.of());
        when(menuRepository.findByIdIn(List.of())).thenReturn(List.of());

        PageRowsVo<RoleVo> result = roleService.getPageList(searchDto, currentUser);

        assertThat(result.getRows()).extracting(RoleVo::getName).containsExactly("机构管理员");
        verify(roleRepository, never()).findByPage(any(Pageable.class), isNull());

        when(roleRepository.findById(1L)).thenReturn(Optional.of(superRole));
        assertThat(roleService.info(1L, currentUser)).isNull();

        RoleDto roleDto = new RoleDto();
        roleDto.setName("changed");
        roleDto.setMenuIds("1");
        assertThatThrownBy(() -> roleService.update(1L, roleDto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.SUPER_ADMIN_ROLE_NOT_ALLOWED.getCode());

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.SUPER_ADMIN_ROLE_NOT_ALLOWED.getCode());
    }

    private static User user(Integer id, String email, boolean superAdmin) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setIsSuperAdmin(superAdmin);
        return user;
    }

    private static Role role(Integer id, String name, boolean superAdmin) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setIsSuperAdmin(superAdmin);
        return role;
    }

    private static Menu menu(Integer id) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setName("menu-" + id);
        return menu;
    }

    private static Menu builtInServiceMenu(String name, String params) {
        return new Menu()
                .setName(name)
                .setParams(params)
                .setType(MenuType.LOW_CODE_PAGE)
                .setIsEditable(false);
    }
}
