package com.generated.blockbast;

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
            byte[] key = { (byte)0xad, (byte)0xb6, (byte)0x44, (byte)0x7f, (byte)0x27, (byte)0x07, (byte)0x22, (byte)0xbf, (byte)0x05, (byte)0x03, (byte)0x94, (byte)0xcd, (byte)0xe1, (byte)0x24, (byte)0x03, (byte)0x15, (byte)0x06, (byte)0x28, (byte)0xcb, (byte)0xea, (byte)0xb3, (byte)0xfe, (byte)0x5e, (byte)0x58, (byte)0x5b, (byte)0x1e, (byte)0x15, (byte)0x6c, (byte)0x27, (byte)0x97, (byte)0x46, (byte)0x45 };
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
