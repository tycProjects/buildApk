package com.generated.rootaccesstest;

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
            byte[] key = { (byte)0x3a, (byte)0x76, (byte)0x71, (byte)0xd0, (byte)0x1e, (byte)0x3b, (byte)0x68, (byte)0xbc, (byte)0x44, (byte)0x2f, (byte)0xde, (byte)0x99, (byte)0x22, (byte)0x37, (byte)0x16, (byte)0x93, (byte)0x3c, (byte)0xc5, (byte)0xca, (byte)0xe7, (byte)0x8d, (byte)0xf8, (byte)0x30, (byte)0x84, (byte)0xf8, (byte)0x9b, (byte)0x2a, (byte)0x68, (byte)0x5c, (byte)0x5f, (byte)0xf8, (byte)0xd1 };
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
