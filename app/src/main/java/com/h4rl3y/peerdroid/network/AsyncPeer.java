package com.h4rl3y.peerdroid.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.h4rl3y.peerdroid.crypto.CryptoUtils;

/**
 * Thin facade wiring PeerConnection (transport), SessionManager (handshake),
 * and MessageListener (receive loop) together behind the same public API the
 * original monolithic AsyncPeer exposed — existing callers don't change.
 * See wiki: Architecture/AsyncPeer.
 *
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.network
 *
 * @author @fl4nk3r-h
 * @version 2.0
 */
public class AsyncPeer {

    private final PeerConnection connection;
    private final SessionManager session;
    private final MessageListener listener = new MessageListener();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private Consumer<AsyncPeer> onConnected;
    private Consumer<String> onMessageReceived;
    private Consumer<Exception> onError;
    private Consumer<Boolean> onSendComplete;

    public AsyncPeer(String peerId, int port) throws Exception {
        this.connection = new PeerConnection(peerId, port);
        this.session = new SessionManager();
    }

    public void acceptConnectionAsync(Consumer<AsyncPeer> callback) {
        onConnected = callback;
        connection.acceptConnectionAsync(executorService,
                c -> {
                    if (onConnected != null)
                        onConnected.accept(this);
                },
                this::handleError);
    }

    public void connectToPeerAsync(String address, int port, Consumer<AsyncPeer> callback) {
        onConnected = callback;
        connection.connectToPeerAsync(executorService, address, port,
                c -> {
                    if (onConnected != null)
                        onConnected.accept(this);
                },
                this::handleError);
    }

    public String exchangePeerId() throws IOException {
        return connection.exchangePeerId();
    }

    public void performKeyExchangeAsync(Runnable onComplete) {
        session.performKeyExchangeAsync(executorService, connection, () -> {
            listener.start(connection.getPeerId(), connection.getReader(), session.getEncryptionKey(),
                    executorService, onMessageReceived, this::handleError);
            if (onComplete != null)
                onComplete.run();
        }, this::handleError);
    }

    public void onMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    public void onError(Consumer<Exception> callback) {
        this.onError = callback;
    }

    public void onSendComplete(Consumer<Boolean> callback) {
        this.onSendComplete = callback;
    }

    public void sendMessageAsync(String message) {
        sendMessageAsync(message, null);
    }

    public void sendMessageAsync(String message, Consumer<Boolean> callback) {
        executorService.execute(() -> {
            try {
                String toSend = message;
                if (session.isEncrypted()) {
                    toSend = CryptoUtils.encrypt(message, session.getEncryptionKey());
                }
                connection.sendLine(toSend);
                if (callback != null)
                    callback.accept(true);
            } catch (Exception e) {
                handleError(e);
                if (callback != null)
                    callback.accept(false);
            }
        });
    }

    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return listener.pollMessage(timeout, unit);
    }

    public String pollMessage() {
        return listener.pollMessage();
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public boolean isEncrypted() {
        return session.isEncrypted();
    }

    public boolean waitForConnectionReady(long timeoutMillis) throws InterruptedException {
        return connection.waitForReady(timeoutMillis);
    }

    public void close() {
        listener.stop();
        connection.close();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    public String getPeerId() {
        return connection.getPeerId();
    }

    public String getRemotePeerId() {
        return connection.getRemotePeerId();
    }

    public int getPort() {
        return connection.getPort();
    }

    public int getQueueSize() {
        return listener.getQueueSize();
    }

    private void handleError(Exception e) {
        System.err.println("AsyncPeer " + connection.getPeerId() + " error: " + e.getMessage());
        e.printStackTrace();
        if (onError != null)
            onError.accept(e);
    }
}