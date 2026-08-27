package com.coolxer.service.system.impl;

import com.coolxer.service.system.CryptService;
import com.coolxer.utils.RsaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * RSA算法接口实现类
 */
@Service
@Slf4j
public class CryptServiceImpl implements CryptService {

    @Override
    public String decryptByRsaPublicKey(String body, String publicKey) {
        String decryptString = null;
        String charsetName = "UTF-8";
        try {
            byte[] bodyBytes = Base64.getDecoder().decode(body);
            byte[] decryptBytes = RsaUtils.decryptByPublicKey(bodyBytes, publicKey);
            decryptString = new String(decryptBytes, charsetName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return decryptString;
    }

    @Override
    public String decryptByRsaPrivateKey(String body, String privateKey) {
        try {
            byte[] decodeBase64Bytes = Base64.getDecoder().decode(body);
            byte[] decryptBytes = RsaUtils.decryptByPrivateKey(decodeBase64Bytes, privateKey);
            return new String(decryptBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("", e);
        }
        return "";
    }


    @Override
    public String decryptLicenseString(String body) {
        // 这写死在代码中，不轻易变动，不放在配置文件中是想隐藏下，不暴露.
        StringBuilder publicKey = new StringBuilder();
        publicKey.append("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmiIjW9");
        publicKey.append("y/Va9usxf2CI8MenluoD1ABrOARKzQc6egPvtby6B0uFfn6bee3JY7zp2gQ7+W4");
        publicKey.append("o8WcobaziMUNd2ZItvf13QXCUnvlz/kj8bUXFyR6O8ESI8mPJr0ZWbrg+GNnXwNm4l+kHGh");
        publicKey.append("hMPZkMYjmAohEUy+XOgj8gNVFo3uWmix9ptSJ+jDBuDpMLvkTjwkzrvI3kv8qfHaGgvvd4");
        publicKey
                .append("PD4hWFnF/hdmD0T85e32qekywg+Oi4A13i5L5tXLgtycs6KbCbENVZkP2Uyf+dj+QBziQ/5l5EtkzMo");
        publicKey.append("K0C9i105+dMM3W34OwCxlbN13bJOG7qBGSnogn3puLkyRD/4NbXKQIDAQAB");
        return decryptByRsaPublicKey(body, publicKey.toString());
    }


}
