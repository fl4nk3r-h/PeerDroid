package com.h4rl3y.peerdroid.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.KeyAgreement;

/**
 * Diffie-Hellman implementation for key exchange.
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.Crypto
 * Description: This class implements the Diffie-Hellman key exchange algorithm
 * 
 * @author @fl4nk3r-h
 * @version 1.0
 * @see com.h4rl3y.peerdroid.crypto.CryptoUtils
 */
public class KeyExchange {
    private KeyPair keyPair;
    private KeyAgreement keyAgreement;

    /**
     * Constructor for the KeyExchange class. Initializes the key pair and key
     * agreement.
     */
    public KeyExchange() throws Exception {
        initializeDiffieHellman();
    }

    /**
     * Get the Base64-encoded public key string.
     * 
     * @return The Base64-encoded public key string.
     * @throws Exception if encoding fails.
     */
    public String getPublicKeyString() {
        byte[] encodedKey = keyPair.getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(encodedKey);
    }

    /**
     * Generate shared secret given the other party's Base64-encoded public key
     *
     * @param otherPublicKeyString Base64-encoded public key of the other party
     * @return byte[] shared secret
     * @throws Exception if key agreement fails
     */
    public byte[] generateSharedSecret(String otherPublicKeyString) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(otherPublicKeyString);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("DH");
        PublicKey otherPublicKey = keyFactory.generatePublic(x509KeySpec);

        keyAgreement.doPhase(otherPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * Generate shared secret and return as Base64-encoded string
     *
     * @param otherPublicKeyString Base64-encoded public key of the other party
     * @return Base64-encoded shared secret string
     * @throws Exception if key agreement fails
     */
    public String getSharedSecretString(String otherPublicKeyString) throws Exception {
        byte[] sharedSecret = generateSharedSecret(otherPublicKeyString);
        return Base64.getEncoder().encodeToString(sharedSecret);
    }

    /**
     * Initialize Diffie-Hellman keypair and KeyAgreement
     *
     * @throws Exception if initialization fails
     */
    private void initializeDiffieHellman() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(1024);
        keyPair = keyPairGenerator.generateKeyPair();

        keyAgreement = KeyAgreement.getInstance("DH");
        keyAgreement.init(keyPair.getPrivate());
    }
}