package com.coolxer.utils;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 密码验证工具类
 */
public class RsaUtils {

    public static final String KEY_ALGORITHM = "RSA";

    private static final String PUBLIC_KEY = "RSAPublicKey";

    private static final String PRIVATE_KEY = "RSAPrivateKey";

    private static final int KEY_LENGTH = 2048;

    private static final int MAX_ENCRYPT_BLOCK = 245;

    private static final int MAX_DECRYPT_BLOCK = 256;

    /**
     * 一对公私钥，用来前端传输密码加密.
     */
    public static String PUB_KY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxF8RUNQOAV/Zd9dlz4BuP+kjYsYhPazj1oQvRVfcvQ3ho9lv"
                    +
                    "5mq9MJ4Zg0XA9K5/d402nfNqr7HucezHgTHwaGSVYqT8HjRgVcKCAOfKgEosV0VJFYLnpdcR79nUGop/1XY/udyc/p83R0oSwhYnqi+3F231EK8V8sLNn5ph9DRZJqvWEq"
                    +
                    "h3trhJoYMvDdnIGq2p9+pXQWKGg9zCTwqBpEVxwh7C8zVPXaS6z4epncFpk5v5Ty0nkBiqudUavRh8Mtjhl8zZpjuClxl29i5Ou2VBhCzPiQn0RTmnVpe/oX6ZYWLaB"
                    +
                    "ThG208lmPw8StD/E5xGuvUgdREYKw9A4/hhZwIDAQAB";

    public static String PRI_KY =
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDEXxFQ1A4BX9l312XPgG4/6SNixiE9rOPWhC9FV" +
                    "9y9DeGj2W/mar0wnhmDRcD0rn93jTad82qvse5x7MeBMfBoZJVipPweNGBVwoIA58qASixXRUkVguel1xHv2dQain/Vdj+53Jz+nzdHShLCFieqL7cXbfUQrxXyws2fm"
                    +
                    "mH0NFkmq9YSqHe2uEmhgy8N2cgaran36ldBYoaD3MJPCoGkRXHCHsLzNU9dpLrPh6mdwWmTm/lPLSeQGKq51Rq9GHwy2OGXzNmmO4KXGXb2Lk67ZUGELM+JCfRFOadWl7+h"
                    +
                    "fplhYtoFOEbbTyWY/DxK0P8TnEa69SB1ERgrD0Dj+GFnAgMBAAECggEAH5NRy3dtkRcCdtyxyD3UfH5WbEmDitFECXhTyYipO9UBlUEfVyOOrESfwZXzwuRUJrsULy221+16c+M"
                    +
                    "o6UNIbuDNjIEO8SRm4SNXmCGtVBeZLac8azMTYH1GqxSRoHrot0A+Wvp4XBDp6JKmlYLOefUWMbRClZ4xn0lnmuROH+AKo/FcVMuyE0VY9hH4j35mR6vQrwbZVGh2zCOehwPUmpSCq"
                    +
                    "BR3pwGIxVZyHYLQIg06l5VknxRqu96ALymncd+qy14qdb+JJv8o6NF7VhrSdnVejIFLbfJRWFew578pRNb6LFNmwP7pKDuyTKF3GHs0yMxlCeddHwOQXJN0idh80QKBgQDzf0XDLBH"
                    +
                    "WSzba98TV45dfy2IIYgCMBL/8XG17e0xUFlOBAf2cyi6mbw+2Ej+GM9sja/xHBeIWNfFm2ybufLkQdz9cEm6Ze/FyS5yjX6GgKf9jCoLQcaOqUZAGg2o++OA6N83hFIaIve2De0owj"
                    +
                    "NIWhJtKV/onBw7zOE3CSW0wFQKBgQDOdFWxlDv/EgYp7YwL4K1A9Axpfy+zWb40Vh2iyP8lk43StcDbpFtysFBUwv4NoEhm+q6bfOo9gowIBR2pwsAwJCIyx+5iNgogbQ0TJhGl7+bjo"
                    +
                    "OB2iM47T7VCMWJvzJ1simAuhWA2JKYHjkZvMvvRF2em9w+ZOFpLbM75cj2uiwKBgAMJFiSNuxcxzthB+Hk2Ih/2mB1Eo38bXA1YVaERc20k7huQm8nFl3lJryd06MfSg6vYX8e+4gem2oCO"
                    +
                    "wNh+Q4xaADc0n55YNjVXjfdXbNtjSqTAb8sLb5/i7mm2X6+zSJBRO8bPi7HOtFRMSCt8xe2z73+owxyTcPNFQ2rloW/VAoGAE6t5zqV04eRxueTsBuWtHBckZ5i6jrfqK0pZIH1N8eKRDJZM"
                    +
                    "faC3JcIupSS+18WBoG4Z3K6WEq4xcUIrhVUMwoluyK/Lwoerh1Ncf4qM++ZTdTSKrkByhawedOQHoOc5yHGWoFgMxpvPRcEEbOvKmi1Ert+zGMIJuE3xmkqMHekCgYAQSiGU4Rm/ykXLsO"
                    +
                    "4tXdKCmdcSOmhzxpB1kz/4LEp5l3VjD/ciXftEspiAIR3/ZLxWhLyyG9R47mdCiZ2WeMPssFjU7HaDve8ucXaoPXb2ezTnGGqyUefFEr3fg4GY/wXGE3y1V9rJlBKpeB26dtz/lHLkuFaXJ8"
                    +
                    "DNHjI7CpGZmg==";


    public static Map<String, Object> genKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGen.initialize(KEY_LENGTH);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        Map<String, Object> keyMap = new HashMap<String, Object>(2);
        keyMap.put(PUBLIC_KEY, publicKey);
        keyMap.put(PRIVATE_KEY, privateKey);
        return keyMap;
    }

    public static byte[] decryptByPrivateKey(byte[] encryptedData, String privateKey)
            throws Exception {
        byte[] keyBytes = decryptBase64(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, privateK);
        int inputLen = encryptedData.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;

        while (inputLen - offSet > 0) {
            if (inputLen - offSet > MAX_DECRYPT_BLOCK) {
                cache = cipher.doFinal(encryptedData, offSet, MAX_DECRYPT_BLOCK);
            } else {
                cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * MAX_DECRYPT_BLOCK;
        }
        byte[] decryptedData = out.toByteArray();
        out.close();
        return decryptedData;
    }

    public static byte[] decryptByPublicKey(byte[] encryptedData, String publicKey)
            throws Exception {
        byte[] keyBytes = decryptBase64(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, publicK);
        int inputLen = encryptedData.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;

        while (inputLen - offSet > 0) {
            if (inputLen - offSet > MAX_DECRYPT_BLOCK) {
                cache = cipher.doFinal(encryptedData, offSet, MAX_DECRYPT_BLOCK);
            } else {
                cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * MAX_DECRYPT_BLOCK;
        }
        byte[] decryptedData = out.toByteArray();
        out.close();
        return decryptedData;
    }

    public static byte[] encryptByPublicKey(byte[] data, String publicKey)
            throws Exception {
        byte[] keyBytes = decryptBase64(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);

        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicK);
        int inputLen = data.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] cache;
        int i = 0;

        while (inputLen - offSet > 0) {
            if (inputLen - offSet > MAX_ENCRYPT_BLOCK) {
                cache = cipher.doFinal(data, offSet, MAX_ENCRYPT_BLOCK);
            } else {
                cache = cipher.doFinal(data, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * MAX_ENCRYPT_BLOCK;
        }
        byte[] encryptedData = out.toByteArray();
        out.close();
        return encryptedData;
    }

    public static String getPrivateKey(Map<String, Object> keyMap) {
        Key key = (Key) keyMap.get(PRIVATE_KEY);
        return encryptBase64(key.getEncoded());
    }

    public static String getPublicKey(Map<String, Object> keyMap) {
        Key key = (Key) keyMap.get(PUBLIC_KEY);
        return encryptBase64(key.getEncoded());
    }

    public static byte[] decryptBase64(String key) {
        return Base64.getDecoder().decode(key);
    }

    public static String encryptBase64(byte[] key) {
        return Base64.getEncoder().encodeToString(key);
    }

}
