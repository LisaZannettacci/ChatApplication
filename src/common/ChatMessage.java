package common;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    public int id;
    public int senderId;
    public String senderName;
    public String content;
    public long timestamp;

    public ChatMessage(int id, int senderId, String senderName, String content) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}