package com.h4rl3y.peerdroid.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.h4rl3y.peerdroid.R;
import com.h4rl3y.peerdroid.model.Message;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MessageAdapter — two-view-type RecyclerView adapter for chat bubbles.
 *
 * Usage in ChatActivity:
 *   MessageAdapter adapter = new MessageAdapter();
 *   binding.messagesRecyclerView.setLayoutManager(
 *       new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
 *   binding.messagesRecyclerView.setAdapter(adapter);
 *
 * Add messages:
 *   adapter.addMessage(new Message("hello", true));   // sent by self
 *   adapter.addMessage(new Message("hi back", false)); // from peer
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SELF = 0;
    private static final int VIEW_TYPE_PEER = 1;

    private final List<Message> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());


    // ── ViewHolders ───────────────────────────────────────────────
    static class SelfViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timestamp;

        SelfViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.tvMessageText);
            timestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timestamp;

        PeerViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.tvMessageText);
            timestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }

    // ── Adapter overrides ─────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSelf ? VIEW_TYPE_SELF : VIEW_TYPE_PEER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SELF) {
            return new SelfViewHolder(inflater.inflate(R.layout.item_message_self, parent, false));
        } else {
            return new PeerViewHolder(inflater.inflate(R.layout.item_message_peer, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        String time = timeFormat.format(new Date(msg.timestampMs));

        if (holder instanceof SelfViewHolder) {
            SelfViewHolder h = (SelfViewHolder) holder;
            h.messageText.setText(msg.text);
            h.timestamp.setText(time);
        } else {
            PeerViewHolder h = (PeerViewHolder) holder;
            h.messageText.setText(msg.text);
            h.timestamp.setText(time);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // ── Public API ────────────────────────────────────────────────
    /**
     * Add a message and scroll to bottom.
     * Call from main thread; your socket handler should post() to main thread first.
     */
    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clear() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }
}