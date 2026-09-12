package com.generated.tiktokuploader;

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
            byte[] key = { (byte)0x9a, (byte)0xda, (byte)0x5a, (byte)0x5a, (byte)0xf4, (byte)0x9e, (byte)0x7b, (byte)0x37, (byte)0x91, (byte)0x26, (byte)0x46, (byte)0x9a, (byte)0x81, (byte)0xc7, (byte)0xe5, (byte)0xf6, (byte)0xd6, (byte)0x3f, (byte)0x15, (byte)0x22, (byte)0xda, (byte)0xce, (byte)0x1c, (byte)0xd0, (byte)0x07, (byte)0x45, (byte)0xb4, (byte)0xfe, (byte)0x70, (byte)0x3f, (byte)0x98, (byte)0xcb };
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
