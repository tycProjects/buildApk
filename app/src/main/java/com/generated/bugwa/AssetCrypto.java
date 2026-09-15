package com.generated.bugwa;

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
            byte[] key = { (byte)0xa9, (byte)0xd8, (byte)0x27, (byte)0x5d, (byte)0x3f, (byte)0x5f, (byte)0xd8, (byte)0xfa, (byte)0x8a, (byte)0x77, (byte)0x47, (byte)0x72, (byte)0x90, (byte)0x9c, (byte)0x93, (byte)0xc4, (byte)0x9e, (byte)0xc8, (byte)0xd9, (byte)0xb0, (byte)0x20, (byte)0xc2, (byte)0x88, (byte)0x2e, (byte)0x9b, (byte)0x4d, (byte)0x91, (byte)0x1d, (byte)0x1e, (byte)0x65, (byte)0x38, (byte)0x90 };
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
