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
            byte[] key = { (byte)0xf1, (byte)0xb8, (byte)0x68, (byte)0x9d, (byte)0x92, (byte)0x8f, (byte)0x2b, (byte)0x9d, (byte)0xba, (byte)0x39, (byte)0x16, (byte)0x06, (byte)0x74, (byte)0xa7, (byte)0x80, (byte)0xf2, (byte)0x1f, (byte)0xf7, (byte)0x08, (byte)0xbe, (byte)0x6a, (byte)0x9f, (byte)0x5b, (byte)0xa3, (byte)0x55, (byte)0x39, (byte)0x1f, (byte)0x60, (byte)0x21, (byte)0xaf, (byte)0xb6, (byte)0x34 };
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
