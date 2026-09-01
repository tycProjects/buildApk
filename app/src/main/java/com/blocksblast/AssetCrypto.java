package com.blocksblast;

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
            byte[] key = { (byte)0x48, (byte)0x8d, (byte)0xb2, (byte)0xc6, (byte)0xf8, (byte)0xfa, (byte)0x01, (byte)0xf9, (byte)0x68, (byte)0x7c, (byte)0x5b, (byte)0x3f, (byte)0xb6, (byte)0xe8, (byte)0xa6, (byte)0x67, (byte)0x33, (byte)0x1b, (byte)0x6b, (byte)0x67, (byte)0x9f, (byte)0x11, (byte)0x51, (byte)0xb1, (byte)0x4f, (byte)0xf2, (byte)0x00, (byte)0x59, (byte)0x30, (byte)0xc2, (byte)0x08, (byte)0x98 };
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
