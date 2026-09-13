package com.generated.aimlock;

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
            byte[] key = { (byte)0x6f, (byte)0x1d, (byte)0x4a, (byte)0xc4, (byte)0x9a, (byte)0x5e, (byte)0xe3, (byte)0x05, (byte)0x0d, (byte)0xc5, (byte)0xdc, (byte)0x4d, (byte)0xa4, (byte)0x8a, (byte)0x46, (byte)0x83, (byte)0xec, (byte)0x03, (byte)0xeb, (byte)0xc2, (byte)0xd4, (byte)0xc4, (byte)0x70, (byte)0xdd, (byte)0xc6, (byte)0x57, (byte)0x9d, (byte)0x62, (byte)0x94, (byte)0x6e, (byte)0x37, (byte)0xa9 };
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
