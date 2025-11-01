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

    /**
     * 根据ID删除聊天记录
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    boolean deleteChatSession(String sessionId);

    /**
     * 根据标题删除聊天记录
     * @param title 会话标题
     * @return 是否删除成功
     */
    boolean deleteChatSessionByTitle(String title);

    /**
     * 删除所有聊天记录
     * @return 删除的会话数量
     */
    int deleteAllChatSessions();
}
