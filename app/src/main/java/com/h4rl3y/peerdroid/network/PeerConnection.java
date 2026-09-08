package com.h4rl3y.peerdroid.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Owns the raw socket lifecycle: accepting or initiating a TCP connection,
 * wrapping it in text streams, and exchanging peer identifiers.
 * Has no knowledge of encryption — SessionManager and MessageListener build
 * on top of the streams this class exposes.
 *
 * Status: In Development
 * Location: com.h4rl3y.peerdroid.network
 *
 * @author @fl4nk3r-h
 * @version 1.0
 */
public class PeerConnection {

    private final String peerId;
    private final int port;
    private String remotePeerId;

    private final ServerSocket serverSocket;
    private Socket peerSocket;
    private PrintWriter out;
    private BufferedReader in;

    public PeerConnection(String peerId, int port) throws IOException {
        this.peerId = peerId;
        this.port = port;
        this.serverSocket = new ServerSocket(port);
    }

    /** Accepts an incoming connection asynchronously on the given executor. */
    public void acceptConnectionAsync(ExecutorService executor, Consumer<PeerConnection> onConnected,
            Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                peerSocket = serverSocket.accept();
                out = new PrintWriter(peerSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
                if (onConnected != null)
                    onConnected.accept(this);
            } catch (IOException e) {
                if (onError != null)
                    onError.accept(e);
            }
        });
    }

    /**
     * Initiates a connection to a remote peer asynchronously on the given executor.
     */
    public void connectToPeerAsync(ExecutorService executor, String address, int remotePort,
            Consumer<PeerConnection> onConnected, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                peerSocket = new Socket(address, remotePort);
                out = new PrintWriter(peerSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
                if (onConnected != null)
                    onConnected.accept(this);
            } catch (IOException e) {
                if (onError != null)
                    onError.accept(e);
            }
        });
    }

    /**
     * Sends the local peerId and blocks for the remote peerId. Call after connect,
     * before key exchange.
     */
    public String exchangePeerId() throws IOException {
        if (out == null || in == null) {
            throw new IOException("I/O streams not initialized. Cannot exchange peerId.");
        }
        out.println("PEER_ID:" + peerId);
        String line = in.readLine();
        if (line == null || !line.startsWith("PEER_ID:")) {
            throw new IOException("Invalid peerId exchange message: " + line);
        }
        remotePeerId = line.substring("PEER_ID:".length());
        return remotePeerId;
    }

    /**
     * Writes a single line to the peer — used for handshake messages and encrypted
     * payloads alike.
     */
    public void sendLine(String line) {
        out.println(line);
    }

    /**
     * Exposes the raw reader for SessionManager (handshake) and MessageListener
     * (message loop).
     */
    public BufferedReader getReader() {
        return in;
    }

    public boolean isConnected() {
        return peerSocket != null && peerSocket.isConnected() && !peerSocket.isClosed();
    }

    public boolean waitForReady(long timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (out != null && in != null && peerSocket != null && peerSocket.isConnected()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    public void close() {
        try {
            if (peerSocket != null && !peerSocket.isClosed())
                peerSocket.close();
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException ignored) {
            // Already closed
        }
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
        } catch (IOException ignored) {
            // Already closed
        }
    }

    public String getPeerId() {
        return peerId;
    }

    public String getRemotePeerId() {
        return remotePeerId;
    }

    public int getPort() {
        return port;
    }
}