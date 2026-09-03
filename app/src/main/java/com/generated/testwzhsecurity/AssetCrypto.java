package com.generated.testwzhsecurity;

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
            byte[] key = { (byte)0xef, (byte)0x60, (byte)0x61, (byte)0xac, (byte)0xb8, (byte)0x96, (byte)0xe4, (byte)0x10, (byte)0xd2, (byte)0x68, (byte)0x2b, (byte)0x63, (byte)0x9e, (byte)0x12, (byte)0xa4, (byte)0x6d, (byte)0x77, (byte)0xd6, (byte)0x60, (byte)0x05, (byte)0x85, (byte)0xbb, (byte)0xc4, (byte)0x35, (byte)0xf3, (byte)0x41, (byte)0xc1, (byte)0xbd, (byte)0xcc, (byte)0x55, (byte)0x0f, (byte)0x3a };
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
