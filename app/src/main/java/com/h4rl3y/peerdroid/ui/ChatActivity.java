package com.h4rl3y.peerdroid.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.h4rl3y.peerdroid.network.*;
import com.h4rl3y.peerdroid.R;
import com.h4rl3y.peerdroid.model.Message;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Chat screen for the P2P-only AsyncPeer backend.
 *
 * This activity owns the socket/session lifecycle, binds incoming messages to the
 * RecyclerView adapter, and sends local messages through the peer backend.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PEER_NAME = "extra_peer_name";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_LOCAL_PORT = "extra_local_port";
    public static final String EXTRA_REMOTE_HOST = "extra_remote_host";
    public static final String EXTRA_REMOTE_PORT = "extra_remote_port";

    private static final String MODE_LISTEN = "listen";
    private static final String MODE_CONNECT = "connect";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sessionExecutor = Executors.newSingleThreadExecutor();

    private MessageAdapter adapter;
    private AsyncPeer peer;

    private MaterialButton btnBack;
    private MaterialButton btnTerminate;
    private TextView tvPeerId;
    private TextView tvEncryptionStatus;
    private RecyclerView messagesRecyclerView;
    private TextInputEditText inputMessage;
    private MaterialButton btnSend;

    private String peerName;
    private String mode;
    private int localPort;
    private String remoteHost;
    private int remotePort;
    private volatile boolean readyToChat;

    public static Intent newIntent(
            @NonNull Context context,
            @NonNull String peerName,
            @NonNull String mode,
            int localPort,
            String remoteHost,
            int remotePort) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_PEER_NAME, peerName);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_LOCAL_PORT, localPort);
        intent.putExtra(EXTRA_REMOTE_HOST, remoteHost);
        intent.putExtra(EXTRA_REMOTE_PORT, remotePort);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        bindViews();

        adapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(adapter);

        readIntentExtras();
        bindUi();
        startSession();
    }

    @Override
    protected void onDestroy() {
        finishSession();
        super.onDestroy();
    }

    private void readIntentExtras() {
        Intent intent = getIntent();
        peerName = intent.getStringExtra(EXTRA_PEER_NAME);
        mode = intent.getStringExtra(EXTRA_MODE);
        localPort = intent.getIntExtra(EXTRA_LOCAL_PORT, 0);
        remoteHost = intent.getStringExtra(EXTRA_REMOTE_HOST);
        remotePort = intent.getIntExtra(EXTRA_REMOTE_PORT, 0);

        if (peerName == null || peerName.isBlank()) {
            peerName = "Peer";
        }
        if (mode == null || mode.isBlank()) {
            mode = MODE_LISTEN;
        }
    }

    private void bindUi() {
        tvPeerId.setText(peerName);
        tvEncryptionStatus.setText(mode.equals(MODE_LISTEN)
                ? "Preparing listener…"
                : "Preparing connection…");
        btnSend.setEnabled(false);

        btnTerminate.setOnClickListener(v -> finishSessionAndExit());
        btnBack.setOnClickListener(v -> finishSessionAndExit());
        btnSend.setOnClickListener(v -> sendCurrentMessage());
        inputMessage.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendCurrentMessage();
                return true;
            }
            return false;
        });
    }

    private void startSession() {
        sessionExecutor.execute(() -> {
            try {
                peer = new AsyncPeer(peerName, localPort);
                peer.onMessageReceived(message -> post(() -> {
                    if (message == null) {
                        // This signifies the remote peer has closed the connection
                        handleRemoteDisconnect();
                        return;
                    }
                    adapter.addMessage(new Message(message, false));
                    scrollMessagesToBottom();
                }));
                peer.onError(this::handlePeerError);

                if (MODE_LISTEN.equals(mode)) {
                    post(() -> tvEncryptionStatus.setText("Listening on port " + localPort + "…"));
                    CountDownLatch accepted = new CountDownLatch(1);
                    peer.acceptConnectionAsync(p -> accepted.countDown());
                    if (!accepted.await(30, TimeUnit.SECONDS)) {
                        throw new TimeoutException("No peer connected within 30 seconds.");
                    }
                } else {
                    if (remoteHost == null || remoteHost.isBlank()) {
                        throw new IllegalArgumentException("Remote host is required in connect mode.");
                    }
                    post(() -> tvEncryptionStatus.setText("Connecting to " + remoteHost + ':' + remotePort + "…"));
                    CountDownLatch connected = new CountDownLatch(1);
                    peer.connectToPeerAsync(remoteHost, remotePort, p -> connected.countDown());
                    if (!connected.await(30, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Unable to connect within 30 seconds.");
                    }
                }

                if (!peer.waitForConnectionReady(15000)) {
                    throw new TimeoutException("I/O streams did not become ready in time.");
                }

                String remotePeerId = peer.exchangePeerId();
                post(() -> {
                    tvPeerId.setText(remotePeerId);
                    tvEncryptionStatus.setText("Exchanging keys…");
                });

                CountDownLatch keyExchangeDone = new CountDownLatch(1);
                peer.performKeyExchangeAsync(keyExchangeDone::countDown);
                if (!keyExchangeDone.await(20, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Key exchange did not complete in time.");
                }

                readyToChat = true;
                post(() -> {
                    tvEncryptionStatus.setText("E2E encrypted · connected");
                    btnSend.setEnabled(true);
                    adapter.addMessage(new Message("Secure P2P session ready.", false));
                    scrollMessagesToBottom();
                });
            } catch (Exception e) {
                handlePeerError(e);
            }
        });
    }
    private void handleRemoteDisconnect() {
        post(() -> {
            readyToChat = false;
            tvEncryptionStatus.setText("Peer disconnected");
            btnSend.setEnabled(false);

            // Show a toast so the user knows why the chat ended
            Toast.makeText(this, "The other peer has ended the session.", Toast.LENGTH_LONG).show();

            // Optional: Wait 2 seconds so they can read the toast, then exit
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2000);
        });
    }
    private void sendCurrentMessage() {
        if (!readyToChat || peer == null || !peer.isConnected()) {
            Toast.makeText(this, "Connection is not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = inputMessage.getText() == null
                ? ""
                : inputMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        inputMessage.setText("");
        peer.sendMessageAsync(text, success -> post(() -> {
            if (success) {
                adapter.addMessage(new Message(text, true));
                scrollMessagesToBottom();
            } else {
                Toast.makeText(this, "Failed to send message.", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void handlePeerError(Exception e) {
        post(() -> {
            if ("CONNECTION_CLOSED".equals(e.getMessage())) {
                tvEncryptionStatus.setText("Peer disconnected");
                Toast.makeText(this, "The other peer has left the chat.", Toast.LENGTH_SHORT).show();

                // Short delay so the user sees the message before the screen closes
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
            } else {
                tvEncryptionStatus.setText("Session error");
                // Optional: only finish on critical errors
                // finish();
            }
            btnSend.setEnabled(false);
        });
    }

    private void finishSessionAndExit() {
        finishSession();
        finish();
    }

    private void finishSession() {
        readyToChat = false;
        if (peer != null) {
            peer.close();
            peer = null;
        }
        sessionExecutor.shutdownNow();
    }

    private void scrollMessagesToBottom() {
        int lastPosition = adapter.getItemCount() - 1;
        if (lastPosition >= 0) {
            messagesRecyclerView.scrollToPosition(lastPosition);
        }
    }

    private void post(@NonNull Runnable runnable) {
        mainHandler.post(runnable);
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnTerminate = findViewById(R.id.btnTerminate);
        tvPeerId = findViewById(R.id.tvPeerId);
        tvEncryptionStatus = findViewById(R.id.tvEncryptionStatus);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        inputMessage = findViewById(R.id.inputMessage);
        btnSend = findViewById(R.id.btnSend);
    }
}


