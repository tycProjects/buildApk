package com.wzh.builder;

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
            byte[] key = { (byte)0xa6, (byte)0xe5, (byte)0xe7, (byte)0x92, (byte)0x3b, (byte)0xff, (byte)0x62, (byte)0x54, (byte)0xa8, (byte)0xdc, (byte)0xe8, (byte)0xa7, (byte)0xab, (byte)0xa4, (byte)0xae, (byte)0x1b, (byte)0x3f, (byte)0x19, (byte)0x95, (byte)0x85, (byte)0x7b, (byte)0xb1, (byte)0xcb, (byte)0xbd, (byte)0x7a, (byte)0xd7, (byte)0x68, (byte)0xf8, (byte)0x43, (byte)0x43, (byte)0x69, (byte)0x0c };
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
