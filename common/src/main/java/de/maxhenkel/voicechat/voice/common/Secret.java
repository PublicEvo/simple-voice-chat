package de.maxhenkel.voicechat.voice.common;

import io.netty.buffer.ByteBuf;
import de.maxhenkel.voicechat.Voicechat;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class Secret {

    public static final int SECRET_SIZE_BYTES = 16;
    public static final int IV_SIZE_BYTES = 12;
    public static final int TAG_LEN_BITS = 128;
    public static final String CIPHER = "AES/GCM/NoPadding";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final SecretKeySpec keySpec;
    private final int compatibilityVersion;

    protected Secret(byte[] secret) {
        this(secret, Voicechat.COMPATIBILITY_VERSION);
    }

    protected Secret(byte[] secret, int compatibilityVersion) {
        this.secret = secret;
        this.keySpec = new SecretKeySpec(secret, "AES");
        this.compatibilityVersion = compatibilityVersion;
    }

    public static Secret generateNewRandomSecret() {
        return generateNewRandomSecret(SECRET_SIZE_BYTES, Voicechat.COMPATIBILITY_VERSION);
    }

    public static Secret generateNewRandomSecret(int size) {
        return generateNewRandomSecret(size, Voicechat.COMPATIBILITY_VERSION);
    }

    public static Secret generateNewRandomSecret(int size, int compatibilityVersion) {
        byte[] secret = new byte[size];
        RANDOM.nextBytes(secret);
        return new Secret(secret, compatibilityVersion);
    }

    public static Secret fromBytes(byte[] secret) {
        return new Secret(secret);
    }

    public static Secret fromBytes(ByteBuf buf) {
        return fromBytes(buf, SECRET_SIZE_BYTES);
    }

    public static Secret fromBytes(ByteBuf buf, int size) {
        return fromBytes(buf, size, Voicechat.COMPATIBILITY_VERSION);
    }

    public static Secret fromBytes(ByteBuf buf, int size, int compatibilityVersion) {
        byte[] secretBytes = new byte[size];
        buf.readBytes(secretBytes);
        return new Secret(secretBytes, compatibilityVersion);
    }

    public static int getSecretSize(int compatibilityVersion) {
        if (compatibilityVersion == 19) {
            return 32;
        } else {
            return 16;
        }
    }

    public void toBytes(ByteBuf buf) {
        buf.writeBytes(secret);
    }

    public byte[] getSecret() {
        return secret;
    }

    public SecretKeySpec getKeySpec() {
        return keySpec;
    }

    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    public byte[] encrypt(byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (compatibilityVersion <= 18) {
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), ivSpec);
            byte[] enc = cipher.doFinal(data);
            byte[] payload = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(enc, 0, payload, iv.length, enc.length);
            return payload;
        } else {
            byte[] iv = generateIV();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] enc = cipher.doFinal(data);
            byte[] payload = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(enc, 0, payload, iv.length, enc.length);
            return payload;
        }
    }

    public byte[] decrypt(byte[] payload) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (compatibilityVersion <= 18) {
            byte[] iv = new byte[16];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            byte[] data = new byte[payload.length - iv.length];
            System.arraycopy(payload, iv.length, data, 0, data.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), ivSpec);
            return cipher.doFinal(data);
        } else {
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_SIZE_BYTES);
            byte[] data = Arrays.copyOfRange(payload, IV_SIZE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), new GCMParameterSpec(TAG_LEN_BITS, iv));
            return cipher.doFinal(data);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Secret)) {
            return false;
        }
        return Arrays.equals(secret, ((Secret) o).secret);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secret);
    }
}
