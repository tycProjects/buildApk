package com.generated.zennostarsv1;

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
            byte[] key = { (byte)0x4e, (byte)0xdb, (byte)0x62, (byte)0x6f, (byte)0xf1, (byte)0x24, (byte)0x7e, (byte)0x8c, (byte)0x01, (byte)0x3a, (byte)0xc8, (byte)0x7e, (byte)0x3b, (byte)0xb4, (byte)0xa1, (byte)0x2d, (byte)0xf4, (byte)0xa2, (byte)0x4b, (byte)0xb1, (byte)0x54, (byte)0x58, (byte)0x44, (byte)0xfd, (byte)0x08, (byte)0xc9, (byte)0x87, (byte)0xcc, (byte)0x87, (byte)0x39, (byte)0xbb, (byte)0xe6 };
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
