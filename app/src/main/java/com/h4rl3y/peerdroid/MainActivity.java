package com.h4rl3y.peerdroid;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.h4rl3y.peerdroid.ui.ChatActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Locale;

/**
 * Launcher activity for the P2P-only Android experience.
 *
 * The connection screen in {@code activity_main.xml} is used to choose whether
 * this device waits for an incoming peer or connects to another peer. Once the
 * user starts a session, the app opens the chat screen backed by
 * {@link com.h4rl3y.peerdroid.network.AsyncPeer}.
 */
public class MainActivity extends AppCompatActivity {

    private static final String MODE_LISTEN = "listen";
    private static final String MODE_CONNECT = "connect";

    private boolean listenMode = true;

    private MaterialButton btnScan;
    private TextView toggleHost;
    private TextView togglePeer;
    private TextView labelHostIp;
    private TextInputLayout inputHostIp;
    private EditText editHostIp;
    private TextView labelPort;
    private EditText editPort;
    private TextView tvLocalIp;
    private MaterialButton btnPrimary;
    private MaterialButton btnShare;
    private View statusDot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        bindListeners();

        setListenMode(true);
    }

    private void bindViews() {
        View searchCard = findViewById(R.id.searchCard);
        btnScan = findViewById(R.id.btnScan);
        toggleHost = findViewById(R.id.toggleHost);
        togglePeer = findViewById(R.id.togglePeer);
        labelHostIp = findViewById(R.id.labelHostIp);
        inputHostIp = findViewById(R.id.inputHostIp);
        editHostIp = findViewById(R.id.editHostIp);
        labelPort = findViewById(R.id.labelPort);
        editPort = findViewById(R.id.editPort);
        tvLocalIp = findViewById(R.id.tvLocalIp);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnShare = findViewById(R.id.btnShare);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);

        searchCard.setVisibility(View.GONE);
    }

    private void bindListeners() {
        btnScan.setOnClickListener(v ->
                Toast.makeText(this, "Peer discovery is disabled in P2P-only mode.", Toast.LENGTH_SHORT).show());
        toggleHost.setOnClickListener(v -> setListenMode(true));
        togglePeer.setOnClickListener(v -> setListenMode(false));
        btnShare.setOnClickListener(v -> shareListeningInfo());
        btnPrimary.setOnClickListener(v -> startPeerSession());
    }

    private void setListenMode(boolean enabled) {
        listenMode = enabled;

        toggleHost.setBackgroundResource(enabled ? R.drawable.toggle_selected : R.drawable.toggle_unselected);
        toggleHost.setTextColor(getColor(enabled ? R.color.md_theme_dark_onPrimaryContainer
                : R.color.md_theme_dark_onSurfaceVariant));

        togglePeer.setBackgroundResource(!enabled ? R.drawable.toggle_selected : R.drawable.toggle_unselected);
        togglePeer.setTextColor(getColor(!enabled ? R.color.md_theme_dark_onPrimaryContainer
                : R.color.md_theme_dark_onSurfaceVariant));

        labelHostIp.setVisibility(enabled ? View.GONE : View.VISIBLE);
        inputHostIp.setVisibility(enabled ? View.GONE : View.VISIBLE);
        tvLocalIp.setVisibility(enabled ? View.VISIBLE : View.GONE);
        btnShare.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (enabled) {
            String localIp = resolveLocalIpAddress();
            int port = parsePort(getText(editPort));
            if (port > 0) {
                tvLocalIp.setText(String.format(Locale.getDefault(), "%s:%d", localIp, port));
            } else {
                tvLocalIp.setText(localIp);
            }
        }

        labelPort.setText(enabled ? "Listen Port" : "Remote Port");
        btnPrimary.setText(enabled ? "Start Listening" : "Connect to Peer");
        statusText.setText(enabled ? "Idle · waiting to listen" : "Idle · ready to connect");
        statusDot.setBackgroundResource(R.drawable.status_dot_idle);
    }

    private void startPeerSession() {
        final int remotePort = parsePort(editPort.getText() == null ? null : editPort.getText().toString());
        if (remotePort <= 0) {
            return;
        }

        String remoteHost = null;
        if (!listenMode) {
            remoteHost = getText(editHostIp);
            if (TextUtils.isEmpty(remoteHost)) {
                showToast("Enter the peer host address.");
                return;
            }
        }

        String peerName = resolvePeerName();
        Intent intent = ChatActivity.newIntent(
                this,
                peerName,
                listenMode ? MODE_LISTEN : MODE_CONNECT,
                listenMode ? remotePort : 0,
                remoteHost,
                remotePort);
        startActivity(intent);
    }

    private void shareListeningInfo() {
        String localIp = resolveLocalIpAddress();
        String port = getText(editPort);
        String message = localIp == null
                ? String.format(Locale.getDefault(), "PeerDroid session ready on port %s", port)
                : String.format(Locale.getDefault(), "PeerDroid session ready at %s:%s", localIp, port);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(shareIntent, "Share P2P endpoint"));
    }

    private int parsePort(String rawValue) {
        if (TextUtils.isEmpty(rawValue)) {
            showToast("Enter a valid port number.");
            return -1;
        }

        try {
            int port = Integer.parseInt(rawValue.trim());
            if (port < 1 || port > 65535) {
                showToast("Port must be between 1 and 65535.");
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            showToast("Port must be numeric.");
            return -1;
        }
    }

    @NonNull
    private String getText(android.widget.EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    @NonNull
    private String resolvePeerName() {
        String model = Build.MODEL == null ? "Peer" : Build.MODEL;
        return model + "-" + Math.abs(System.nanoTime() % 10000);
    }

    private String resolveLocalIpAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return "Local IP:"+inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort only.
        }
        return "Local IP: ERROR NOT FOUND";
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
