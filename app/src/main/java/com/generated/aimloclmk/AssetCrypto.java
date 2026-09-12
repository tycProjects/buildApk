package com.generated.aimloclmk;

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
            byte[] key = { (byte)0xc9, (byte)0xb0, (byte)0x0e, (byte)0xf6, (byte)0xad, (byte)0xa0, (byte)0x1a, (byte)0x56, (byte)0x4c, (byte)0x71, (byte)0xc3, (byte)0xc7, (byte)0xca, (byte)0x7b, (byte)0xc5, (byte)0x93, (byte)0x0a, (byte)0x1f, (byte)0xa9, (byte)0x39, (byte)0xa1, (byte)0x4c, (byte)0x18, (byte)0x76, (byte)0xc3, (byte)0x2f, (byte)0x7b, (byte)0x04, (byte)0x23, (byte)0xbb, (byte)0x5e, (byte)0xb3 };
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
