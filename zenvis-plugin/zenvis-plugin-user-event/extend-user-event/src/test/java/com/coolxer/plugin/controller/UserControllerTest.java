package com.coolxer.plugin.controller;

import com.coolxer.plugin.servicer.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserControllerTest {

    @Test
    void exposesRelativeDynamicPluginRoutes() throws Exception {
        RequestMapping root = UserController.class.getAnnotation(RequestMapping.class);
        PostMapping add = UserController.class.getMethod("add", UserDto.class).getAnnotation(PostMapping.class);
        GetMapping list = UserController.class.getMethod("list", UserSearchDto.class).getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/user"}, root.value());
        assertArrayEquals(new String[]{"/add"}, add.value());
        assertArrayEquals(new String[]{"/list"}, list.value());
    }

    @Test
    void preservesResponseAndPagingShape() {
        UserService service = new UserService() {
            @Override
            public boolean add(UserDto userDto) {
                return true;
            }

            @Override
            public PageRowsVo<UserVo> getPageList(UserSearchDto query) {
                return new PageRowsVo<>(List.of(), 0);
            }
        };
        UserController controller = new UserController(service);
        UserDto request = new UserDto();
        request.setName("tester");

        ResponseWrap<?> response = controller.add(request);
        assertEquals(0, response.getStatus());
        assertEquals("请求成功", response.getMsg());
        assertEquals("添加成功tester", response.getData());
    }
}
