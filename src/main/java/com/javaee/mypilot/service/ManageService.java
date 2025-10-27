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

        ChatMessage response = chatService.handleRequest(sessionId, chatOpt, request, codeReferences);

    }
}
