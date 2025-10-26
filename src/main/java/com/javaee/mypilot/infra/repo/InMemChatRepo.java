package com.javaee.mypilot.infra.repo;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天记录仓库 - 内存实现
 */
@Service(Service.Level.PROJECT)
public final class InMemChatRepo implements IChatRepo{

    private final Project project;
    private final Map<String, ChatSession> sessions = new HashMap<>();
    private final Map<String, ChatSession> title2Session = new HashMap<>();

    public InMemChatRepo(Project project) {
        this.project = project;
    }

    public IChatRepo getInstance(Project project) {
        return project.getService(InMemChatRepo.class);
    }

    /**
     * 保存聊天记录
     * @param chatSession 聊天记录实体
     */
    @Override
    public void saveChatSession(ChatSession chatSession) {

        sessions.put(chatSession.getId(), chatSession);
        title2Session.put(chatSession.getTitle(), chatSession);
    }

    /**
     * 根据id获取聊天记录
     * @param sessionId 聊天记录ID
     * @return 聊天记录实体
     */
    @Override
    public ChatSession getChatSession(String sessionId) {

        return sessions.get(sessionId);
    }

    /**
     * 根据标题获取聊天记录
     * @param title 聊天记录标题
     * @return 聊天记录实体
     */
    @Override
    public ChatSession getChatSessionByTitle(String title) {

        return title2Session.get(title);
    }

    /**
     * 获取所有聊天记录
     * @return 聊天记录列表
     */
    @Override
    public List<ChatSession> getAllChatSessions() {

        return sessions.values().stream().toList();
    }

    /**
     * 获取所有聊天记录标题
     * @return 聊天记录标题列表
     */
    @Override
    public List<String> getAllChatSessionTitles() {

        return title2Session.keySet().stream().toList();
    }
}
