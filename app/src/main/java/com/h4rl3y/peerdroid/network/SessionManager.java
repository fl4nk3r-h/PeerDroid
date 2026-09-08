package com.h4rl3y.peerdroid.network;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import com.h4rl3y.peerdroid.crypto.CryptoUtils;
import com.h4rl3y.peerdroid.crypto.KeyExchange;

/**
 * Runs the handshake over an already-connected PeerConnection and derives
 * the symmetric encryption key. Today this wraps classical DH (KeyExchange).
 * Migrating to ML-KEM-768 + ML-DSA-65 touches only this class and KeyExchange —
 * PeerConnection and MessageListener don't need to change at all.
 *
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.network
 *
 * @author @fl4nk3r-h
 * @version 1.0
 */
public class SessionManager {

    private final KeyExchange keyExchange;
    private volatile SecretKey encryptionKey;

    public SessionManager() throws Exception {
        this.keyExchange = new KeyExchange();
    }

    /**
     * Runs the handshake asynchronously: exchanges public keys, derives the AES
     * key.
     */
    public void performKeyExchangeAsync(ExecutorService executor, PeerConnection connection,
            Runnable onComplete, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                connection.sendLine(keyExchange.getPublicKeyString());
                String peerPublicKey = connection.getReader().readLine();
                String sharedSecret = keyExchange.getSharedSecretString(peerPublicKey);
                encryptionKey = CryptoUtils.getKeyFromString(sharedSecret);
                if (onComplete != null)
                    onComplete.run();
            } catch (Exception e) {
                if (onError != null)
                    onError.accept(e);
            }
        });
    }

    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    public boolean isEncrypted() {
        return encryptionKey != null;
    }
}