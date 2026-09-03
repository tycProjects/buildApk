package com.generated.wzh2apk;

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
            byte[] key = { (byte)0x3a, (byte)0x83, (byte)0xe3, (byte)0xd5, (byte)0xe8, (byte)0x04, (byte)0xb5, (byte)0x0f, (byte)0xc0, (byte)0x9b, (byte)0x82, (byte)0x54, (byte)0xb4, (byte)0xe0, (byte)0x38, (byte)0xdb, (byte)0x93, (byte)0x71, (byte)0xc7, (byte)0x1e, (byte)0xb5, (byte)0x9b, (byte)0x86, (byte)0xae, (byte)0x35, (byte)0xfd, (byte)0x86, (byte)0x14, (byte)0x04, (byte)0x7b, (byte)0x3b, (byte)0x1a };
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
