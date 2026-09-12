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
            byte[] key = { (byte)0xd9, (byte)0xfd, (byte)0x3d, (byte)0xa2, (byte)0x4c, (byte)0x7e, (byte)0x42, (byte)0x04, (byte)0x03, (byte)0x10, (byte)0x8c, (byte)0x94, (byte)0x26, (byte)0xea, (byte)0x03, (byte)0x1d, (byte)0xc6, (byte)0x13, (byte)0x1a, (byte)0x9d, (byte)0xc3, (byte)0x4a, (byte)0x5d, (byte)0x86, (byte)0xe2, (byte)0x13, (byte)0x9f, (byte)0x85, (byte)0x75, (byte)0x30, (byte)0x46, (byte)0x91 };
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
