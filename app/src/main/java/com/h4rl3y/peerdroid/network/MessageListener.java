package com.h4rl3y.peerdroid.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import com.h4rl3y.peerdroid.crypto.CryptoUtils;

/**
 * Dedicated daemon thread that blocks on the socket's input stream, decrypts
 * incoming lines, buffers them in a queue, and dispatches them via callback
 * on the shared executor. Knows nothing about how the key was derived —
 * just needs a SecretKey to decrypt with, so it's untouched by the PQC
 * migration.
 *
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.network
 *
 * @author @fl4nk3r-h
 * @version 1.0
 */
public class MessageListener {

    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private Thread listenerThread;
    private volatile boolean running = false;

    /**
     * Starts the listener thread. Call once, after the handshake has produced a
     * key.
     */
    public void start(String label, BufferedReader reader, SecretKey encryptionKey,
            ExecutorService dispatchExecutor, Consumer<String> onMessageReceived,
            Consumer<Exception> onError) {
        if (running)
            return;
        running = true;

        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    String decrypted = line;
                    try {
                        if (encryptionKey != null) {
                            decrypted = CryptoUtils.decrypt(line, encryptionKey);
                        }
                    } catch (Exception e) {
                        if (onError != null)
                            onError.accept(e);
                        continue;
                    }

                    final String message = decrypted;
                    messageQueue.put(message);
                    dispatchExecutor.execute(() -> {
                        if (onMessageReceived != null)
                            onMessageReceived.accept(message);
                    });
                }

                if (running) {
                    running = false;
                    if (onError != null)
                        onError.accept(new Exception("CONNECTION_CLOSED"));
                }
            } catch (IOException | InterruptedException e) {
                if (running && onError != null)
                    onError.accept(e);
            }
        }, label + "-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        running = false;
        if (listenerThread != null && listenerThread.isAlive()) {
            try {
                listenerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public String pollMessage() {
        return messageQueue.poll();
    }

    public String pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    public int getQueueSize() {
        return messageQueue.size();
    }
}