package com.generated.rezshort;

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
            byte[] key = { (byte)0x58, (byte)0x1f, (byte)0x8d, (byte)0x7a, (byte)0x58, (byte)0x19, (byte)0xee, (byte)0xc0, (byte)0x12, (byte)0x91, (byte)0xa3, (byte)0x7e, (byte)0x88, (byte)0x6c, (byte)0x5c, (byte)0x62, (byte)0x0c, (byte)0x8e, (byte)0x71, (byte)0x99, (byte)0x48, (byte)0x25, (byte)0xad, (byte)0xc7, (byte)0xe8, (byte)0x5f, (byte)0x01, (byte)0x40, (byte)0xfe, (byte)0x31, (byte)0x53, (byte)0x3f };
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
