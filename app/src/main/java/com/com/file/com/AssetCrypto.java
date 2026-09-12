package com.com.file.com;

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
            byte[] key = { (byte)0x17, (byte)0x96, (byte)0xed, (byte)0x61, (byte)0x9e, (byte)0x33, (byte)0x13, (byte)0xc3, (byte)0xc8, (byte)0xa1, (byte)0x5b, (byte)0xb5, (byte)0x03, (byte)0x80, (byte)0xdf, (byte)0x3d, (byte)0xbd, (byte)0x61, (byte)0xa6, (byte)0x78, (byte)0xdf, (byte)0xb6, (byte)0xe9, (byte)0x25, (byte)0x74, (byte)0x07, (byte)0x35, (byte)0x4b, (byte)0x54, (byte)0xd9, (byte)0xf2, (byte)0x6e };
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
