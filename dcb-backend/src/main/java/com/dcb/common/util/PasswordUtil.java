package com.dcb.common.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 密码工具类（PBKDF2WithHmacSHA256，Java 内置，无需额外依赖）
 * 格式：salt:hash
 */
public class PasswordUtil {

    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 对明文密码进行哈希 */
    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /** 验证密码 */
    public static boolean verify(String password, String stored) {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = pbkdf2(password.toCharArray(), salt);
        return java.util.Arrays.equals(expectedHash, actualHash);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }
}
