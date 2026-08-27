package com.coolxer.aop;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.vo.ResponseWrap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 统一异常处理
 */
@Slf4j
@ControllerAdvice
public class ApiExceptionHandler {

    /**
     * JSON 请求体和查询对象的 Bean Validation 校验失败。
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseWrap<String> processValidationException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不正确");
        log.warn("请求参数校验失败: {}", message);
        return ResponseWrap.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * JSON 语法、日期或枚举值无法反序列化。
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseWrap<String> processUnreadableRequest(HttpMessageNotReadableException ex) {
        log.warn("请求体格式不正确: {}", ex.getMostSpecificCause().getMessage());
        return ResponseWrap.fail(HttpStatus.BAD_REQUEST.value(), "请求体格式不正确");
    }

    /**
     * 统一参数异常处理
     *
     * @return 请求结果
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseWrap<String> processMethod(MissingServletRequestParameterException ex) {
        log.error("", ex);
        return ResponseWrap.fail();
    }

    /**
     * 统一异常处理
     *
     * @param ex 异常
     * @return 结果
     */
    @ResponseBody
    @ExceptionHandler(Throwable.class)
    public ResponseWrap<String> processMethod2(Throwable ex) {
        log.error("", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ApiException apiException) {
            status = HttpStatus.valueOf(apiException.getCode());
        } else if (ex instanceof IllegalArgumentException || ex instanceof MissingServletRequestParameterException) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseWrap.fail(status.value(), ex.getMessage());
    }

    /**
     * 自定义异常处理
     *
     * @param apiException 自定义异常
     * @return 请求结果
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler(value = ApiException.class)
    public ResponseWrap<String> customApiExceptionHandler(ApiException apiException) {
        return ResponseWrap.fail(apiException);
    }


}
