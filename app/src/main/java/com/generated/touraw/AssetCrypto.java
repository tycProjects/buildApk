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
            byte[] key = { (byte)0x76, (byte)0x63, (byte)0x6c, (byte)0x71, (byte)0xcb, (byte)0x09, (byte)0xb6, (byte)0xc6, (byte)0xc9, (byte)0xcd, (byte)0x21, (byte)0x51, (byte)0xae, (byte)0x2a, (byte)0x09, (byte)0xb3, (byte)0xa9, (byte)0x99, (byte)0x43, (byte)0x39, (byte)0x14, (byte)0xc3, (byte)0x9c, (byte)0x25, (byte)0x31, (byte)0x2b, (byte)0x69, (byte)0x21, (byte)0x4a, (byte)0x9a, (byte)0x1b, (byte)0xf3 };
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
