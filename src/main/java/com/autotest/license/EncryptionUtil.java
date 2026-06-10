package com.autotest.license;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类 - AES-256-GCM
 * <p>
 * 与服务器端 EncryptionService 兼容。
 * 使用AES-256-GCM模式进行加密/解密，提供认证加密功能。
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // GCM推荐IV长度
    private static final int GCM_TAG_LENGTH = 128; // GCM认证标签长度
    private static final int AES_KEY_SIZE = 256; // AES密钥长度

    // 使用软件ID和固定盐值派生密钥，确保与服务器端兼容
    private static final String KEY_DERIVATION_SALT = "SpringApiTester_License_Salt_v1.0";

    /**
     * 从软件ID派生加密密钥
     *
     * @param softwareId 软件ID
     * @return 派生的密钥字节数组
     */
    public static byte[] deriveKey(String softwareId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = softwareId + "_" + KEY_DERIVATION_SALT;
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 生成随机nonce（IV）
     *
     * @return 随机生成的12字节nonce
     */
    public static byte[] generateNonce() {
        byte[] nonce = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    /**
     * 加密数据
     *
     * @param plaintext  明文
     * @param softwareId 软件ID，用于派生密钥
     * @return Base64编码的密文（包含nonce + ciphertext + auth tag）
     */
    public static String encrypt(String plaintext, String softwareId) {
        try {
            byte[] keyBytes = deriveKey(softwareId);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] nonce = generateNonce();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将nonce和密文拼接: nonce(12 bytes) + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密数据
     *
     * @param ciphertext Base64编码的密文（包含nonce + ciphertext + auth tag）
     * @param softwareId 软件ID，用于派生密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String softwareId) {
        try {
            byte[] keyBytes = deriveKey(softwareId);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            // 提取nonce和密文
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] nonce = new byte[GCM_IV_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 生成随机AES密钥（用于测试或其他用途）
     *
     * @return Base64编码的密钥
     */
    public static String generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES key generation failed", e);
        }
    }

    /**
     * 使用指定密钥加密数据
     *
     * @param plaintext 明文
     * @param keyBytes  密钥字节数组
     * @return Base64编码的密文
     */
    public static String encryptWithKey(String plaintext, byte[] keyBytes) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            byte[] nonce = generateNonce();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + ciphertext.length);
            buffer.put(nonce);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption with key failed", e);
        }
    }

    /**
     * 使用指定密钥解密数据
     *
     * @param ciphertext Base64编码的密文
     * @param keyBytes   密钥字节数组
     * @return 明文
     */
    public static String decryptWithKey(String ciphertext, byte[] keyBytes) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] nonce = new byte[GCM_IV_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption with key failed", e);
        }
    }
}
