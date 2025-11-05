package com.javaee.mypilot.service;


import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.chat.CodeReference;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理器
 * 作为 View 层与所有子 Service 之间的桥梁
 * TODO: idea
 */
@Service(Service.Level.PROJECT)
public final class ManageService {

    private final Project project;
    private final ConfigService configService;
    private final ChatService chatService;
    private final PropertyChangeSupport support;

    private ChatOpt currentOpt = ChatOpt.ASK;
    private String sessionId = null;
    private List<CodeReference> codeReferences = new ArrayList<>();

    public ManageService(@NotNull Project project) {
        this.project = project;
        this.configService = ConfigService.getInstance(project);
        this.chatService = ChatService.getInstance(project);
        this.support = new PropertyChangeSupport(this);
    }

    /**
     * 添加属性变更监听器
     * @param pcl 监听器
     */
    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    /**
     * 移除属性变更监听器
     * @param pcl 监听器
     */
    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    /**
     * 处理请求
     * @param request 用户请求
     */
    public void handleRequest(String request, ChatOpt chatOpt, CodeContext codeContext) {
        if (sessionId == null) {
            sessionId = chatService.startNewChatSession();
            // 首次创建会话时，通知 UI 更新当前会话ID，避免 UI 侧提前创建会话清空代码引用
            support.firePropertyChange("sessionId", null, sessionId);
        }

        // 保存请求时的会话ID，用于验证响应是否属于当前会话
        final String requestSessionId = sessionId;

        // 异步调用 ChatService，传递代码引用
        chatService.handleRequestAsync(sessionId, chatOpt, request, codeReferences)
            .thenAccept(response -> {
                // 检查响应是否属于当前会话（防止切换会话后显示旧会话的响应）
                if (requestSessionId.equals(sessionId)) {
                    // 通知 View 层显示响应
                    support.firePropertyChange("assistantMessage", null, response);
                } else {
                    System.out.println("ManageService: 忽略不属于当前会话的响应 (请求会话: " + requestSessionId + ", 当前会话: " + sessionId + ")");
                }
                
                // 请求完成后清空代码引用
                clearCodeReferences();
            })
            .exceptionally(throwable -> {
                // 检查错误响应是否属于当前会话
                if (requestSessionId.equals(sessionId)) {
                    // 错误处理
                    support.firePropertyChange("error", null, "请求失败: " + throwable.getMessage());
                } else {
                    System.out.println("ManageService: 忽略不属于当前会话的错误响应 (请求会话: " + requestSessionId + ", 当前会话: " + sessionId + ")");
                }
                clearCodeReferences();
                return null;
            });
    }
    
    /**
     * 添加代码引用
     * @param codeReference 代码引用信息
     */
    public void addCodeReference(CodeReference codeReference) {
        if (codeReference != null) {
            codeReferences.add(codeReference);
            System.out.println("添加代码引用: " + codeReference.toString());
            // 通知 View 层更新代码引用显示
            support.firePropertyChange("codeReferencesUpdated", null, new ArrayList<>(codeReferences));
        }
    }
    
    /**
     * 移除指定的代码引用
     * @param index 要移除的引用索引
     */
    public void removeCodeReference(int index) {
        if (index >= 0 && index < codeReferences.size()) {
            codeReferences.remove(index);
            support.firePropertyChange("codeReferencesUpdated", null, new ArrayList<>(codeReferences));
        }
    }
    
    /**
     * 清空所有代码引用
     */
    public void clearCodeReferences() {
        if (!codeReferences.isEmpty()) {
            codeReferences.clear();
            support.firePropertyChange("codeReferencesUpdated", null, new ArrayList<>(codeReferences));
        }
    }
    
    /**
     * 获取当前的代码引用列表
     * @return 代码引用列表的副本
     */
    public List<CodeReference> getCodeReferences() {
        return new ArrayList<>(codeReferences);
    }
    
    /**
     * 获取当前聊天选项
     */
    public ChatOpt getCurrentOpt() {
        return currentOpt;
    }
    
    /**
     * 设置当前聊天选项
     */
    public void setCurrentOpt(ChatOpt opt) {
        ChatOpt oldOpt = this.currentOpt;
        this.currentOpt = opt;
        support.firePropertyChange("chatOpt", oldOpt, opt);
    }
    
    /**
     * 开始新会话
     */
    public void startNewSession() {
        sessionId = chatService.startNewChatSession();
        clearCodeReferences();
        support.firePropertyChange("sessionId", null, sessionId);
    }
    
    /**
     * 获取当前会话ID
     * @return 当前会话ID，如果没有则返回 null
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 获取所有聊天标题
     */
    public List<String> getAllChatTitles() {
        return chatService.showAllChatTitles();
    }

    /**
     * 根据标题切换到指定会话并返回所有消息
     * @param title 会话标题
     * @return 该会话的所有消息列表，如果会话不存在则返回空列表
     */
    public List<ChatMessage> switchToSessionByTitle(String title) {
        com.javaee.mypilot.core.model.chat.ChatSession session = chatService.getChatSessionByTitle(title);
        if (session == null) {
            return new ArrayList<>();
        }
        
        // 切换到该会话
        sessionId = session.getId();
        support.firePropertyChange("sessionId", null, sessionId);
        
        // 返回该会话的所有消息
        List<ChatMessage> messages = session.getMessages();
        return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * 根据标题删除聊天会话
     * @param title 会话标题
     * @return 是否删除成功
     */
    public boolean deleteChatSessionByTitle(String title) {
        // 先获取会话信息，以便检查是否是当前会话
        com.javaee.mypilot.core.model.chat.ChatSession session = chatService.getChatSessionByTitle(title);
        boolean wasCurrentSession = false;
        if (session != null && session.getId().equals(sessionId)) {
            wasCurrentSession = true;
        }
        
        // 删除会话
        boolean deleted = chatService.deleteChatSessionByTitle(title);
        if (deleted) {
            // 如果删除的是当前会话，需要重置 sessionId
            if (wasCurrentSession) {
                sessionId = null;
            }
            // 通知UI更新
            support.firePropertyChange("sessionDeleted", title, null);
        }
        return deleted;
    }

    /**
     * 删除所有聊天会话
     * @return 删除的会话数量
     */
    public int deleteAllChatSessions() {
        // 删除所有会话
        int deletedCount = chatService.deleteAllChatSessions();
        if (deletedCount > 0) {
            // 重置当前会话ID
            sessionId = null;
            // 通知UI更新
            support.firePropertyChange("allSessionsDeleted", deletedCount, null);
        }
        return deletedCount;
    }
    
    public static ManageService getInstance(Project project) {
        return project.getService(ManageService.class);
    }
}
