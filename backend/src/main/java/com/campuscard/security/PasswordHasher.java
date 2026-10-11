package com.campuscard.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Password hashing format shared by account creation and authentication. */
public final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String hash(char[] password) {
        if (password == null) throw new IllegalArgumentException("password");
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        try {
            return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                    + "$" + Base64.getEncoder().encodeToString(derived);
        } finally {
            java.util.Arrays.fill(derived, (byte) 0);
            java.util.Arrays.fill(salt, (byte) 0);
        }
    }

    public static boolean verify(char[] password, String encoded) {
        if (password == null || encoded == null || encoded.length() > 128) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !"pbkdf2".equals(parts[0]) || !Integer.toString(ITERATIONS).equals(parts[1])
                || parts[2].length() != 24 || parts[3].length() != 44) return false;
        byte[] salt = null;
        byte[] expected = null;
        byte[] actual = null;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != SALT_BYTES || expected.length != KEY_BYTES) return false;
            if (!Base64.getEncoder().encodeToString(salt).equals(parts[2])
                    || !Base64.getEncoder().encodeToString(expected).equals(parts[3])) return false;
            actual = derive(password, salt, ITERATIONS);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        } finally {
            if (actual != null) java.util.Arrays.fill(actual, (byte) 0);
            if (expected != null) java.util.Arrays.fill(expected, (byte) 0);
            if (salt != null) java.util.Arrays.fill(salt, (byte) 0);
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("当前环境不支持密码哈希算法", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
