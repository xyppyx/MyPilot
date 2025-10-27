package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;
import com.javaee.mypilot.core.model.agent.AgentResponse;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.infra.agent.DiffManager;
import com.javaee.mypilot.infra.api.AgentPrompt;
import com.javaee.mypilot.infra.api.LlmClient;
import org.jetbrains.annotations.NotNull;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Agent服务类
 * 负责处理与智能代理相关的业务逻辑，包括构建Prompt、调用大语言模型API以及解析响应等。
 */
@Service(Service.Level.PROJECT)
public final class AgentService {

    private final Project project;
    private final LlmClient llmClient;
    private final DiffManager diffManager;
    private static final Gson GSON = new Gson();

    public AgentService(@NotNull Project project) {
        this.project = project;
        this.llmClient = project.getService(LlmClient.class);
        this.diffManager = project.getService(DiffManager.class);
    }

    /**
     * 处理请求
     * @param chatSession 聊天会话
     * @return llm回复
     */
    public ChatMessage handleRequest(ChatSession chatSession) {

        // 构建prompt
        String sessionContext = chatSession.buildSessionContextPrompt(Chat.MAX_CHAT_TURN);
        String codeContext = chatSession.buildCodeContextPrompt();
        String userMessage = chatSession.getLastMessage().getContent();
        String prompt = AgentPrompt.buildPrompt(codeContext, sessionContext, userMessage);

        // 发送请求到llm
        ChatMessage responseMessage;
        try {
            String response = llmClient.chat(prompt);
            AgentResponse agentResponse = parseLlmResponse(response);

            // 构建回复消息
            responseMessage = new ChatMessage(ChatMessage.Type.ASSISTANT, agentResponse.getExplanation());

            // 异步处理代码部分
            if (agentResponse.getCode() != null) {

            }

        } catch (Exception e) {
            responseMessage = new ChatMessage(ChatMessage.Type.SYSTEM, "调用大语言模型接口失败: " + e.getMessage());
        }

        return responseMessage;
    }

    /**
     * 解析llm json返回中的explanation为ChatMessage
     * @param response llm返回的json字符串
     * @return 解析后的ChatMessage
     */
    public AgentResponse parseLlmResponse(String response) {

        try {
            return GSON.fromJson(response, AgentResponse.class);
        } catch (JsonSyntaxException e) {
            return new AgentResponse("无法解析大语言模型的响应: " + e.getMessage(), null);
        }
    }
}
