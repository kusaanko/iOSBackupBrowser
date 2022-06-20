package com.github.kusaanko.iosbackupbrowser;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.github.kusaanko.iosbackupbrowser.Util.*;

public class KeyBag {
    //
    // this section is mostly copied from parts of iphone-dataprotection
    // http://code.google.com/p/iphone-dataprotection/

    private static final List<String> CLASSKEY_TAGS = Arrays.asList("CLAS", "WRAP", "WPKY", "KTYP", "PBKY");  //UUID
    private static final String[] KEYBAG_TYPES = {"System", "Backup", "Escrow", "OTA (icloud)"};
    private static final String[] KEY_TYPES = {"AES", "Curve25519"};
    private static final Map<Integer, String> PROTECTION_CLASSES = new HashMap<Integer, String>() {{
        put(1, "NSFileProtectionComplete");
        put(2, "NSFileProtectionCompleteUnlessOpen");
        put(3, "NSFileProtectionCompleteUntilFirstUserAuthentication");
        put(4, "NSFileProtectionNone");
        put(5, "NSFileProtectionRecovery?");
        put(6, "kSecAttrAccessibleWhenUnlocked");
        put(7, "kSecAttrAccessibleAfterFirstUnlock");
        put(8, "kSecAttrAccessibleAlways");
        put(9, "kSecAttrAccessibleWhenUnlockedThisDeviceOnly");
        put(10, "kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly");
        put(11, "kSecAttrAccessibleAlwaysThisDeviceOnly");
    }};
    private static final int WRAP_DEVICE = 1;
    private static final int WRAP_PASSCODE = 2;

    private int type;
    private byte[] uuid;
    private byte[] wrap;
    private Map<Integer, Map<String, byte[]>> classKeys;
    private Map<String, byte[]> attrs;

    public KeyBag(byte[] backupKeyBag) {
        this.classKeys = new LinkedHashMap<>();
        this.attrs = new LinkedHashMap<>();
        this.parseKeyBag(backupKeyBag);
    }

    private void parseKeyBag(byte[] keyBag) {
        Map<byte[], byte[]> TLVBlocks = getTLVBlocks(keyBag);
        Map<String, byte[]> currentClassKey = new LinkedHashMap<>();
        for (byte[] tag : TLVBlocks.keySet()) {
            String strTag = new String(tag);
            byte[] data = TLVBlocks.get(tag);
            int intData = 0;
            if (data.length == 4) {
                intData = fromBytesToInt(data);
            }
            if (strTag.equals("TYPE")) {
                this.type = intData;
            } else if (strTag.equals("UUID") && this.uuid == null) {
                this.uuid = data;
            } else if (strTag.equals("WRAP") && this.wrap == null) {
                this.wrap = data;
            } else if (strTag.equals("UUID")) {
                if (currentClassKey.size() > 0) {
                    this.classKeys.put(fromBytesToInt(currentClassKey.get("CLAS")), currentClassKey);
                }
                currentClassKey = new LinkedHashMap<>();
                currentClassKey.put("UUID", data);
            } else if (CLASSKEY_TAGS.contains(strTag)) {
                currentClassKey.put(strTag, data);
            } else {
                this.attrs.put(strTag, data);
            }
        }
        if (currentClassKey.size() > 0) {
            this.classKeys.put(fromBytesToInt(currentClassKey.get("CLAS")), currentClassKey);
        }
    }

    public boolean unlockWithPasscode(String passcode) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(passcode.getBytes(StandardCharsets.UTF_8), this.attrs.get("DPSL"), fromBytesToInt(this.attrs.get("DPIC")));
        byte[] dk = ((KeyParameter) gen.generateDerivedParameters(256)).getKey();

        PKCS5S2ParametersGenerator gen2 = new PKCS5S2ParametersGenerator(new SHA1Digest());
        gen2.init(dk, this.attrs.get("SALT"), fromBytesToInt(this.attrs.get("ITER")));
        byte[] dk2 = ((KeyParameter) gen2.generateDerivedParameters(256)).getKey();

        for(Map<String, byte[]> classKey : this.classKeys.values()) {
            if(classKey.containsKey("WPKY")) {
                if((fromBytesToInt(classKey.get("WRAP")) & WRAP_PASSCODE) == WRAP_PASSCODE) {
                    byte[] k = AESUnwrap(dk2, classKey.get("WPKY"));
                    if(k == null) return false;
                    classKey.put("KEY", k);
                }
            }
        }
        return true;
    }

    public byte[] unwrapKeyForClass(int protectionClass, byte[] persistentKey) {
        byte[] ck = this.classKeys.get(protectionClass).get("KEY");
        if(persistentKey.length != 0x28) {
            throw new IllegalArgumentException("Invalid persistent key length! It requires 0x28.");
        }
        return AESUnwrap(ck, persistentKey);
    }

    public void printClassKeys() {
        System.out.println("==KeyBag");
        System.out.println("Keybag type: " + KEYBAG_TYPES[this.type] + " keybag (" + this.type + ")");
        System.out.println("Keybag version: " + fromBytesToInt(this.attrs.get("VERS")));
        System.out.println(strCopy("-", 209));
        System.out.println(
                ljust("Class", 53) +
                        ljust("WRAP", 5) +
                        ljust("Type", 11) +
                        ljust("Key", 65) +
                        ljust("WPKY", 65) +
                        "Public Key"
        );
        System.out.println(strCopy("-", 209));
        for(int cls : this.classKeys.keySet()) {
            Map<String, byte[]> ck = this.classKeys.get(cls);
            if(cls == 6) System.out.println();
            System.out.println(
                    ljust(PROTECTION_CLASSES.get(cls), 53) +
                            ljust("" + fromBytesToInt(ck.getOrDefault("WRAP", new byte[4])), 5) +
                            ljust(KEY_TYPES[fromBytesToInt(ck.getOrDefault("KTYP", new byte[4]))], 11) +
                            ljust(toHex(ck.getOrDefault("KEY", new byte[0])), 65) +
                            ljust(toHex(ck.getOrDefault("WPKY", new byte[0])), 65)
            );
        }
    }

    private byte[] AESUnwrap(byte[] kek, byte[] wrapped) {
        List<BigInteger> C = new ArrayList<>();
        for(int i = 0;i < wrapped.length / 8;i++) {
            C.add(new BigInteger(Arrays.copyOfRange(wrapped, i * 8, i * 8 + 8)));
        }
        int n = C.size() - 1;
        List<BigInteger> R = new ArrayList<>();
        BigInteger A = C.get(0);
        R.add(BigInteger.valueOf(0));
        for(int i = 1;i < n + 1;i++) {
            R.add(C.get(i));
        }
        for(int j = 5;j >= 0;j--) {
            for(int i = n;i >= 1;i--) {
                byte[] todec = new byte[16];
                ByteBuffer buffer = ByteBuffer.allocate(16);
                buffer.putLong(A.xor(BigInteger.valueOf((long) n * j + i)).longValue());
                buffer.putLong(R.get(i).longValue());
                todec = buffer.array();
                SecretKeySpec secretKeySpec = new SecretKeySpec(kek, "AES");
                try {
                    Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
                    byte[] B = cipher.doFinal(todec);
                    A = new BigInteger(Arrays.copyOfRange(B, 0, 8));
                    R.remove(i);
                    R.add(i, new BigInteger(Arrays.copyOfRange(B, 8, 16)));
                } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
                    e.printStackTrace();
                }
            }
        }
        if(!A.equals(BigInteger.valueOf(0xa6a6a6a6a6a6a6a6L))) {
            return null;
        }
        byte[] res = new byte[(R.size() - 1) * 8];
        for(int i = 1;i < R.size();i++) {
            System.arraycopy(ByteBuffer.allocate(8).putLong(R.get(i).longValue()).array(), 0, res, (i - 1) * 8, 8);
        }
        return res;
    }


    private static Map<byte[], byte[]> getTLVBlocks(byte[] datas) {
        int i = 0;
        Map<byte[], byte[]> ret = new LinkedHashMap<>();
        while (i + 8 <= datas.length) {
            byte[] tag = Arrays.copyOfRange(datas, i, i + 4);
            int length = fromBytesToInt(Arrays.copyOfRange(datas, i + 4, i + 8));
            byte[] data = Arrays.copyOfRange(datas, i + 8, i + 8 + length);
            i += 8 + length;
            ret.put(tag, data);
        }
        return ret;
    }

}
