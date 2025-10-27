package com.javaee.mypilot.infra.repo;

import com.javaee.mypilot.core.model.chat.*;

import java.util.List;

/**
 * 聊天记录仓库接口
 */
public interface IChatRepo {

    /**
     * 保存聊天记录
     * @param chatSession 聊天记录实体
     */
    void saveChatSession(ChatSession chatSession);

    /**
     * 根据id获取聊天记录
     * @param sessionId 聊天记录ID
     * @return 聊天记录实体
     */
    ChatSession getChatSession(String sessionId);

    /**
     * 获取所有聊天记录
     * @return 聊天记录列表
     */
    List<ChatSession> getAllChatSessions();

    /**
     * 获取所有聊天记录标题
     * @return 聊天记录标题列表
     */
    List<String> getAllChatSessionTitles();
}
