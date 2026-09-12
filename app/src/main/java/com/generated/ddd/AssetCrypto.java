package com.generated.ddd;

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
            byte[] key = { (byte)0xe2, (byte)0x17, (byte)0xdb, (byte)0x10, (byte)0x08, (byte)0x11, (byte)0x3e, (byte)0x13, (byte)0xd1, (byte)0x18, (byte)0xf7, (byte)0x67, (byte)0xa5, (byte)0x12, (byte)0xf6, (byte)0x07, (byte)0x1a, (byte)0x4e, (byte)0xb3, (byte)0xd0, (byte)0x47, (byte)0x7d, (byte)0x14, (byte)0x31, (byte)0x02, (byte)0x26, (byte)0x43, (byte)0x89, (byte)0x83, (byte)0xc9, (byte)0xa4, (byte)0x14 };
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
