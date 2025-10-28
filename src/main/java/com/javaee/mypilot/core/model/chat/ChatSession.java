package com.javaee.mypilot.core.model.chat;

import com.javaee.mypilot.core.consts.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 聊天会话模型
 */
public class ChatSession {
    
    private String id;                          // 会话唯一标识, 36位UUID
    private String title;                       // 会话标题
    private List<ChatMessage> messages;         // 消息列表
    private List<CodeContext> codeContexts;     // 最新对话的代码上下文
    private Integer offset;                     // 消息偏移量(对话压缩后, 新增消息的起始位置)
    private ChatMeta meta;                      // 会话元信息
    private Integer tokenUsage;                 // token使用情况
    
    public ChatSession() {
        this.id = UUID.randomUUID().toString();
        this.messages = new ArrayList<>();
        this.title = Chat.DEFAULT_TITLE;
        this.tokenUsage = 0;  // 初始化 token 使用量为 0
        this.offset = 0;      // 初始化偏移量为 0
    }
    
    public ChatSession(String title) {
        this();
        this.title = title;
    }

    /**
     * 添加消息到会话
     */
    public void addMessage(ChatMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        
        // 如果是用户的第一条消息，自动生成会话标题
        if (messages.size() == 1 && message.isUserMessage()) {
            generateTitleFromFirstMessage(message);
        }
    }
    
    /**
     * 移除指定消息
     */
    public boolean removeMessage(String messageId) {
        if (messages == null) return false;
        return messages.removeIf(msg -> messageId.equals(msg.getId()));
    }
    
    /**
     * 获取最后一条消息
     */
    public ChatMessage getLastMessage() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.getLast();
    }

    /**
     * 构建对话上下文string
     * @param count 包含的最后count轮对话
     * @return prompt字符串
     */
    public String buildSessionContextPrompt(int count) {
        StringBuilder prompt = new StringBuilder();
        if (meta != null) {
            prompt.append("对话历史摘要:\n");
            prompt.append(meta.toString()).append("\n");
        }

        if (messages != null) {
            prompt.append("对话历史:\n");
            int start = Math.max(0, messages.size() - count * 2);
            for (int i = start; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                String role = msg.isUserMessage() ? "用户" : "助手";
                prompt.append(role).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return prompt.toString().trim();
    }

    /**
     * 构建代码上下文string
     * @return 代码上下文字符串
     */
    public String buildCodeContextPrompt() {
        StringBuilder prompt = new StringBuilder();
        if (codeContexts != null && !codeContexts.isEmpty()) {
            prompt.append("代码上下文:\n");
            for (CodeContext ctx : codeContexts) {
                prompt.append(ctx.formatContext()).append("\n---\n");
            }
        }
        return prompt.toString().trim();
    }

    /**
     * 获取消息数量
     */
    public int getMessageCount() {
        return messages == null ? 0 : messages.size();
    }
    
    /**
     * 清空所有消息
     */
    public void clearMessages() {
        if (messages != null) {
            messages.clear();
        }
    }
    
    /**
     * 根据第一条消息生成会话标题
     */
    private void generateTitleFromFirstMessage(ChatMessage firstMessage) {
        if (firstMessage != null && firstMessage.getContent() != null) {
            String content = firstMessage.getContent().trim();
            // 取前30个字符作为标题
            if (content.length() > 30) {
                this.title = content.substring(0, 30) + "...";
            } else {
                this.title = content;
            }
        }
    }
    
    /**
     * 检查会话是否为空
     */
    public boolean isEmpty() {
        return messages == null || messages.isEmpty();
    }
    
    /**
     * 获取会话预览（最后一条消息的片段）
     */
    public String getPreview() {
        ChatMessage lastMsg = getLastMessage();
        if (lastMsg == null || lastMsg.getContent() == null) {
            return "空会话";
        }
        String content = lastMsg.getContent().trim();
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
    
    @Override
    public String toString() {
        return "ChatSession{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", messageCount=" + getMessageCount() +
                '}';
    }

    // Getter & Setter

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public ChatMeta getMeta() {
        return meta;
    }

    public void setMeta(ChatMeta meta) {
        this.meta = meta;
    }

    public Integer getTokenUsage() {
        return tokenUsage;
    }

    public void setTokenUsage(Integer tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public List<CodeContext> getCodeContexts() {
        return codeContexts;
    }

    public void setCodeContexts(List<CodeContext> codeContexts) {
        this.codeContexts = codeContexts;
    }
}