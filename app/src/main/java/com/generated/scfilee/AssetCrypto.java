package com.generated.scfilee;

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
            byte[] key = { (byte)0x3d, (byte)0x4f, (byte)0x60, (byte)0x3b, (byte)0x94, (byte)0xb9, (byte)0x54, (byte)0x01, (byte)0x7e, (byte)0xc5, (byte)0x7b, (byte)0x1d, (byte)0xe2, (byte)0xfd, (byte)0xcb, (byte)0x5e, (byte)0x39, (byte)0xda, (byte)0x07, (byte)0x7a, (byte)0xd0, (byte)0xf0, (byte)0xaa, (byte)0x65, (byte)0xbc, (byte)0x73, (byte)0x1b, (byte)0xb2, (byte)0xbb, (byte)0x2d, (byte)0x5c, (byte)0xd2 };
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
