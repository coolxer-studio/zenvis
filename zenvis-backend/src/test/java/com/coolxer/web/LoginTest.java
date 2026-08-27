package com.coolxer.web;

import com.coolxer.utils.BCrypt;
import com.coolxer.utils.RsaUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

/**
 * desc
 */

class LoginTest {

    @Test
    void loginTest() throws Exception {
        String password = "coolxer@!QAZ2wsx";
        System.out.println("原始密码 加密");
//    System.out.println(BCrypt.gensalt());
        System.out.println(BCrypt.hashpw(password, "$2a$10$kCtgK9s26iFIZJXFQpO6De"));
        System.out.println(encrypt(password));

    }

    private static String encrypt(String password) throws Exception {
        byte[] a = RsaUtils.encryptByPublicKey(password.getBytes(), RsaUtils.PUB_KY);
        byte[] result = Base64.getEncoder().encode(a);
        return new String(result, "UTF-8");
    }
}
