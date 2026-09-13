package com.generated.tdataimlock;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Decrypts EmbeddedAssets' bundled site content (AES-256-CBC, one random
// IV per file prepended to that file's ciphertext). The key intentionally
// lives inside decrypt()'s method body -- see the comment in
// embedAssetsAsCode (server.js) for exactly why that placement matters
// once Dex2C hardening runs on this class.
final class AssetCrypto {
    static byte[] decrypt(byte[] ivAndCipherText) {
        try {
            byte[] key = { (byte)0x8d, (byte)0x2a, (byte)0x46, (byte)0x93, (byte)0x9a, (byte)0x06, (byte)0x55, (byte)0x4a, (byte)0xbc, (byte)0xc3, (byte)0x5a, (byte)0x4c, (byte)0xd0, (byte)0x41, (byte)0x53, (byte)0x91, (byte)0x9e, (byte)0xc3, (byte)0xf1, (byte)0xd0, (byte)0xbb, (byte)0x9c, (byte)0x60, (byte)0x10, (byte)0x05, (byte)0x3a, (byte)0x18, (byte)0x44, (byte)0xf8, (byte)0x16, (byte)0x86, (byte)0xa7 };
            byte[] iv = new byte[16];
            System.arraycopy(ivAndCipherText, 0, iv, 0, 16);
            byte[] cipherText = new byte[ivAndCipherText.length - 16];
            System.arraycopy(ivAndCipherText, 16, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("Asset decrypt failed", e);
        }
    }

    private AssetCrypto() {}
}
