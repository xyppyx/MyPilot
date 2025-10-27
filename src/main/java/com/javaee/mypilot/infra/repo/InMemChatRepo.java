package com.javaee.mypilot.infra.repo;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.*;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天记录仓库 - 内存实现
 */
@Service(Service.Level.PROJECT)
public final class InMemChatRepo implements IChatRepo{

    private final Project project;
    private final Object lock = new Object();
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

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
     * 获取所有聊天记录
     * @return 聊天记录列表
     */
    @Override
    public List<ChatSession> getAllChatSessions() {

        return sessions.values().stream().toList();
    }
}
