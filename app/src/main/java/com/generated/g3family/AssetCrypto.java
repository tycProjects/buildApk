package com.generated.g3family;

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
            byte[] key = { (byte)0x71, (byte)0x12, (byte)0xb6, (byte)0x50, (byte)0xdf, (byte)0xcc, (byte)0x49, (byte)0xfd, (byte)0x95, (byte)0x73, (byte)0xcb, (byte)0x0d, (byte)0x68, (byte)0xb2, (byte)0xa1, (byte)0x67, (byte)0x5d, (byte)0xf2, (byte)0x1a, (byte)0x42, (byte)0xc2, (byte)0x6f, (byte)0x5d, (byte)0xcf, (byte)0xc9, (byte)0xc8, (byte)0x3c, (byte)0x3b, (byte)0x33, (byte)0x5a, (byte)0xf8, (byte)0x75 };
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
