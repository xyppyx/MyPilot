package com.javaee.mypilot.core.model.chat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 聊天消息模型
 */
public class ChatMessage {
    
    /**
     * 消息类型枚举
     */
    public enum Type {
        USER("user"),           // 用户消息
        ASSISTANT("assistant"), // AI助手消息
        SYSTEM("system");       // 系统消息
        
        private final String value;
        
        Type(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    private String id;                          // 消息唯一标识, 36位UUID
    private Type type;                          // 消息类型
    private String content;                     // 消息内容
    private LocalDateTime timestamp;            // 消息时间戳
    
    public ChatMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }
    
    public ChatMessage(Type type, String content) {
        this();
        this.type = type;
        this.content = content;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Type getType() {
        return type;
    }
    
    public void setType(Type type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * 检查是否为用户消息
     */
    public boolean isUserMessage() {
        return type == Type.USER;
    }
    
    /**
     * 检查是否为AI助手消息
     */
    public boolean isAssistantMessage() {
        return type == Type.ASSISTANT;
    }
    
    /**
     * 追加内容（用于流式传输）
     */
    public void appendContent(String additionalContent) {
        if (this.content == null) {
            this.content = additionalContent;
        } else {
            this.content += additionalContent;
        }
    }
    
    /**
     * 获取格式化的时间戳
     */
    public String getFormattedTimestamp() {
        if (timestamp == null) return "";
        return timestamp.toString(); // 可以根据需要自定义格式
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", content='" + (content != null ? content.substring(0, Math.min(50, content.length())) : "") + "...'" +
                ", timestamp=" + timestamp +
                '}';
    }
}