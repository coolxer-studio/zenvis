package com.coolxer.controller.system;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.LoginDto;
import com.coolxer.model.system.vo.LoginVo;
import com.coolxer.model.system.vo.PubKeyVo;
import com.coolxer.service.system.LoginService;
import com.coolxer.utils.RsaUtils;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

/**
 * 登录
 */
@Tag(name = "登录")
@Slf4j
@RestController
@RequestMapping("/api/v1/system/login")
public class LoginController extends BaseController {


    @Autowired
    private DefaultKaptcha defaultKaptcha;

    @Autowired
    private LoginService loginService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    public HttpServletRequest request;

    /**
     * 用户登录
     *
     * @param loginDto 登录参数
     * @return 结果
     */
    @PostMapping(value = "/sign-in")
    @Operation(summary = "用户登录", description = "用于用户登录")
    public ResponseWrap<LoginVo> login(@RequestBody LoginDto loginDto) {
        // 检测验证码
        loginService.checkAuthCode(loginDto.getAuthCode(), request);
        User user = loginService.checkUserAndPasswd(loginDto);
        // 重置sessionID,渗透测试提出的要求
        resetSessionId(request);
        //返回数据
        return ResponseWrap.success(loginService.returnData(user, request));
    }

    /**
     * 验证码
     *
     * @param request  请求对象
     * @param response 响应对象
     * @return 返回图片
     * @throws IOException io异常
     */
    @GetMapping(value = "/kaptcha")
    @Operation(summary = "验证码", description = "用于生成验证码")
    public String kaptCha(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        //生产验证码字符串并保存到session中
        String strCode = defaultKaptcha.createText();
        String strSessions = request.getSession().getId();
        stringRedisTemplate.opsForValue().set(strSessions + "strCode", strCode);
        stringRedisTemplate.expire(strSessions + "strCode", Duration.ofSeconds(120));
        //将图片输出给浏览器
        BufferedImage imageCode = defaultKaptcha.createImage(strCode);
        response.setContentType("image/png");
        OutputStream os = response.getOutputStream();
        ImageIO.write(imageCode, "png", os);
        os.flush();
        return strCode;
    }

    /**
     * 返回用来做密码传输加密的公钥key.
     */
    @GetMapping(value = "/encrypt/key")
    @Operation(summary = "公钥接口", description = "返回用来做密码传输加密的公钥key")
    public ResponseWrap<PubKeyVo> encryptKey() {
        PubKeyVo pubKeyVo = new PubKeyVo();
        pubKeyVo.setKey(RsaUtils.PUB_KY);
        return ResponseWrap.success(pubKeyVo);
    }

    /**
     * 退出登录接口.
     */
    @PostMapping(value = "/sign-out")
    @Operation(summary = "退出接口", description = "退出登录接口")
    public ResponseWrap<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = request.getRequestedSessionId();
        if (StringUtils.isEmpty(sessionId)) {
            return ResponseWrap.fail(ResultCodeEnum.PLEASE_LOGIN);
        }
        stringRedisTemplate.delete(sessionId);
        // 清除cookie
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (sessionId.equals(cookie.getValue())) {
                cookie.setMaxAge(0);
                cookie.setValue("");
                cookie.setPath("/");
                response.addCookie(cookie);
            }
        }
        return ResponseWrap.success();
    }

}
