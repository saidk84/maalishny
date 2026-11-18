package com.tabtaba.app;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class Backup {
    private static final byte[] HEADER = {'T', 'A', 'B', 1};
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_SIZE = 16;
    private static final int NONCE_SIZE = 12;

    static byte[] encrypt(byte[] data, char[] password) throws Exception {
        byte[] salt = new byte[SALT_SIZE];
        byte[] nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);
        byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce).doFinal(data);
        return ByteBuffer.allocate(HEADER.length + salt.length + nonce.length + encrypted.length)
            .put(HEADER).put(salt).put(nonce).put(encrypted).array();
    }

    static byte[] decrypt(byte[] file, char[] password) throws Exception {
        if (file.length < HEADER.length + SALT_SIZE + NONCE_SIZE + 16 ||
                !Arrays.equals(HEADER, Arrays.copyOf(file, HEADER.length))) {
            throw new IllegalArgumentException("Unsupported backup");
        }
        ByteBuffer buffer = ByteBuffer.wrap(file);
        buffer.position(HEADER.length);
        byte[] salt = new byte[SALT_SIZE];
        byte[] nonce = new byte[NONCE_SIZE];
        buffer.get(salt);
        buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        return cipher(Cipher.DECRYPT_MODE, password, salt, nonce).doFinal(encrypted);
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] nonce)
            throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 600_000, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(HEADER);
            return cipher;
        } finally {
            spec.clearPassword();
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }
}
