package com.javaee.mypilot.service;


import com.intellij.openapi.components.Service;

import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.chat.ChatMessage;
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
        }

        // 调用 ChatService，传递代码引用
        ChatMessage response = chatService.handleRequest(sessionId, chatOpt, request, codeReferences);
        
        // 通知 View 层显示响应
        support.firePropertyChange("assistantMessage", null, response);
        
        // 请求完成后清空代码引用
        clearCodeReferences();
    }
    
    /**
     * 添加代码引用
     * @param codeReference 代码引用信息
     */
    public void addCodeReference(CodeReference codeReference) {
        if (codeReference != null) {
            codeReferences.add(codeReference);
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
     * 获取所有聊天标题
     */
    public List<String> getAllChatTitles() {
        return chatService.showAllChatTitles();
    }
    
    public static ManageService getInstance(Project project) {
        return project.getService(ManageService.class);
    }
}
