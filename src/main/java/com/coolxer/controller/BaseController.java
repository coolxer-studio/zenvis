package com.coolxer.controller;

import com.coolxer.aop.AuthorityInterceptor;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 控制器公共方法
 */
@Slf4j
@RestController
public class BaseController {

    @Autowired
    public HttpServletRequest request;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    protected User getSessionUser() {
        Object bearerUserId = request.getAttribute(AuthorityInterceptor.API_BEARER_USER_ID_ATTR);
        if (bearerUserId instanceof Integer userId) {
            return userRepository.findById(userId);
        }
        if (bearerUserId instanceof String userId && StringUtils.isNotEmpty(userId)) {
            return userRepository.findById(Integer.valueOf(userId));
        }

        String sessionId = request.getRequestedSessionId();
        if (StringUtils.isEmpty(sessionId)) {
            return null;
        }
        HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
        Map<String, String> mapSession = hashOperations.entries(sessionId);
        String userId = mapSession.get("strUid");
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        return userRepository.findById(Integer.valueOf(userId));
    }

    protected void resetSessionId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        log.debug("old session id: " + session.getId());

        // 首先将原session中的数据转移至一临时map中
        Map<String, Object> tempMap = new HashMap<>(16);
        Enumeration<String> sessionNames = session.getAttributeNames();

        while (sessionNames.hasMoreElements()) {
            String sessionName = sessionNames.nextElement();
            tempMap.put(sessionName, session.getAttribute(sessionName));
        }

        // 注销原session，为的是重置sessionId
        session.invalidate();

        // 将临时map中的数据转移至新session
        session = request.getSession(true);

        log.debug("New session id: " + session.getId());

        for (Map.Entry<String, Object> entry : tempMap.entrySet()) {
            session.setAttribute(entry.getKey(), entry.getValue());
        }
    }

}
