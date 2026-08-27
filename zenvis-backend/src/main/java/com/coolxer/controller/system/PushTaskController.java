package com.coolxer.controller.system;

import com.coolxer.service.system.PushTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 推送任务
 */
@Tag(name = "推送任务")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/push-task")
public class PushTaskController {

    @Autowired
    private PushTaskService pushTaskService;

    @RequestMapping("/**")
    public Object proxy(HttpServletRequest request) {
        return pushTaskService.proxy(request);
    }

}
