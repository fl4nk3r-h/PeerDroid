package com.h4rl3y.peerdroid.model;

/**
 * Immutable chat message used by the RecyclerView adapter to display messages in the chat.
 *
 * @author @h4rl3y-q
 * @version 1.0
 * @see com.h4rl3y.peerdroid.ui.MessageAdapter
 */
public class Message {
    public final String text;
    public final boolean isSelf;
    public final long timestampMs;

    public Message(String text, boolean isSelf) {
        this(text, isSelf, System.currentTimeMillis());
    }
    public Message(String text, boolean isSelf, long timestampMs) {
        this.text = text;
        this.isSelf = isSelf;
        this.timestampMs = timestampMs;
    }
}
