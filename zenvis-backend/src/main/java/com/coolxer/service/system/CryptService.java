package com.coolxer.service.system;

/**
 * RSA算法接口
 */
public interface CryptService {

    /**
     * RSA算法解密，通过公钥key.
     *
     * @param body      待解密的字符串
     * @param publicKey 公钥
     * @return 解密结果
     */
    public String decryptByRsaPublicKey(String body, String publicKey);

    /**
     * RSA算法解密，通过私钥key.
     *
     * @param body       待解密的字符串
     * @param privateKey 私钥
     * @return 解密结果
     */
    String decryptByRsaPrivateKey(String body, String privateKey);

    /**
     * 解密license，密钥默认写死在代码里，不暴露出来.
     *
     * @param body 待解密的字符串
     * @return 结果
     */
    public String decryptLicenseString(String body);
}
