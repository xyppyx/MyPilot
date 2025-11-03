package com.javaee.mypilot.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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

import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<ChatMessage> handleRequestAsync(ChatSession chatSession) {

        // 构建prompt
        String sessionContext = chatSession.buildSessionContextPrompt(Chat.MAX_CHAT_TURN);
        String codeContext = chatSession.buildCodeContextPrompt();
        String userMessage = chatSession.getLastMessage().getContent();
        String prompt = AgentPrompt.buildPrompt(codeContext, sessionContext, userMessage);
        System.out.println("Agent模式提示词: " + prompt);

        // 异步调用llm client
        try {
            return llmClient.chatAsync(prompt)
                    .thenApply(this::parseLlmResponse)
                    .thenApply(agentResponse -> {
                        ChatMessage responseMessage = new ChatMessage(ChatMessage.Type.ASSISTANT, agentResponse.getExplanation());
                        // 异步处理代码变更
                        CompletableFuture.runAsync(() -> {
                            System.out.println();
                            diffManager.handleCodeChanges(agentResponse.getCodeActions());
                        });

                        System.out.println("Agent模式响应: " + GSON.toJson(agentResponse));
                        return responseMessage;
                    });
        } catch (Exception e) {
            System.out.println("Agent响应错误" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析llm返回json为AgentResponse
     * @param response llm返回的json字符串
     * @return 解析后的AgentResponse
     */
    public AgentResponse parseLlmResponse(String response) {

        try {
            System.out.println("解析LLM AGENT响应: " + response);
            return GSON.fromJson(response, AgentResponse.class);
        } catch (JsonSyntaxException e) {
            return new AgentResponse("无法解析大语言模型的响应: " + e.getMessage(), null);
        }
    }
}
