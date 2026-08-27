package com.coolxer.aop;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.utils.JacksonUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * 拦截器抽象类
 */
@Slf4j
public abstract class AbstractInterceptor implements HandlerInterceptor {

    /**
     * 把错误信息写入HTTP响应对象中.
     *
     * @param response       HTTP响应对象
     * @param resultCodeEnum 错误信息
     * @throws IOException IOException
     */
    protected void writErrorInfoToResponse(HttpServletResponse response, ResultCodeEnum resultCodeEnum) throws IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(Objects.requireNonNull(JacksonUtil.toJson(ResponseWrap.fail(resultCodeEnum))));
        }

    }
}
