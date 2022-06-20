package com.github.kusaanko.iosbackupbrowser;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Util {
    public static String ljust(String str, int size) {
        StringBuilder strBuilder = new StringBuilder(str);
        while (strBuilder.length() < size) {
            strBuilder.append(" ");
        }
        str = strBuilder.toString();
        return str;
    }

    public static String strCopy(String str, int time) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < time; i++) {
            ret.append(str);
        }
        return ret.toString();
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for(byte b : bytes) {
            String bStr = Integer.toHexString(b & 0xFF);
            if(bStr.length() == 1) builder.append("0");
            builder.append(bStr);
        }
        return builder.toString();
    }

    public static int fromBytesToInt(byte[] data) {
        return ByteBuffer.wrap(data).getInt();
    }

    public static byte[] AESDecryptCBC(byte[] data, byte[] key, byte[] iv, boolean padding) {
        if(iv.length % 16 > 0) {
            iv = Arrays.copyOfRange(iv, 0, iv.length / 16 * 16);
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            byte[] ret = cipher.doFinal(data);
            if(padding) {
                return Arrays.copyOfRange(ret, 16, ret.length);
            }
            return ret;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] AESDecryptCBC(byte[] data, byte[] key) {
        return AESDecryptCBC(data, key, new byte[16], false);
    }

    public static byte[] readFromInputStream(InputStream stream) throws IOException {
        byte[] buff = new byte[8192];
        int len;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while((len = stream.read(buff)) != -1) {
            baos.write(buff, 0, len);
        }
        return baos.toByteArray();
    }
}
