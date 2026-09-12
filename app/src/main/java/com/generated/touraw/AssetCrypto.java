package com.generated.touraw;

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
            byte[] key = { (byte)0x2d, (byte)0x88, (byte)0x1b, (byte)0x65, (byte)0x13, (byte)0x70, (byte)0xed, (byte)0xbe, (byte)0x0a, (byte)0x4d, (byte)0xca, (byte)0x00, (byte)0xe0, (byte)0xaf, (byte)0xa7, (byte)0xe8, (byte)0xa7, (byte)0xba, (byte)0x98, (byte)0x0d, (byte)0x81, (byte)0xbb, (byte)0x99, (byte)0x4e, (byte)0xf3, (byte)0x74, (byte)0xa4, (byte)0x71, (byte)0xa9, (byte)0x1d, (byte)0x89, (byte)0xa4 };
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
