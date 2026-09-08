package com.h4rl3y.peerdroid.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Utility class for cryptographic operations.
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.Crypto
 * Description: This class provides utility methods for performing cryptographic
 * operations.
 * 
 * @author @fl4nk3r-h
 * @version 1.0
 */
public class CryptoUtils {
    /**
     * Encrypts the given data using AES encryption with the provided secret key.
     * 
     * @param data The data to encrypt.
     * @param key  The secret key to use for encryption.
     * @return The encrypted data as a Base64-encoded string.
     * @throws Exception If an error occurs during encryption.
     */
    public static String encrypt(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData); 
    }

    /**
     * Decrypts the given encrypted data using AES decryption with the provided
     * secret key.
     * 
     * @param encryptedData The encrypted data as a Base64-encoded string.
     * @param key           The secret key to use for decryption.
     * @return The decrypted data as a string.
     * @throws Exception If an error occurs during decryption.
     */
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedData = cipher.doFinal(decodedData);
        return new String(decryptedData);
    }

    /**
     * Generates a new random AES secret key.
     * 
     * @return A new AES secret key.
     * @throws Exception If an error occurs during key generation.
     */
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }
}
