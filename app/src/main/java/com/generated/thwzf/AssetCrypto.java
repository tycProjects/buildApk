package com.generated.thwzf;

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
            byte[] key = { (byte)0x92, (byte)0x44, (byte)0x60, (byte)0xf9, (byte)0xd6, (byte)0x10, (byte)0xae, (byte)0x90, (byte)0x53, (byte)0xb5, (byte)0xa7, (byte)0x7f, (byte)0x4b, (byte)0x70, (byte)0x15, (byte)0x0c, (byte)0xb9, (byte)0x36, (byte)0x3c, (byte)0x70, (byte)0x95, (byte)0xe6, (byte)0x86, (byte)0x75, (byte)0x12, (byte)0xbb, (byte)0x8f, (byte)0xf8, (byte)0xad, (byte)0x57, (byte)0xa3, (byte)0xc5 };
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
