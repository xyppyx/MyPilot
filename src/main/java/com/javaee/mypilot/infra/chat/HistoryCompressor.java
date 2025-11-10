package com.javaee.mypilot.infra.chat;

import com.google.gson.Gson;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.ChatMeta;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.infra.api.LlmClient;

import java.util.concurrent.CompletableFuture;

/**
 * 聊天记录压缩器，负责对聊天记录进行压缩和摘要，以便节省存储空间和提高检索效率。
 * TODO: edit
 */
@Service(Service.Level.PROJECT)
public final class HistoryCompressor {

    private static final String PROMPT_TEMPLATE = """
        **角色：专业的 AI 编程会话总结专家。**
        
        **指令：**
        1.  你将接收一段 AI 编程聊天记录。
        2.  请严格分析对话内容，提取核心信息，并填充以下 JSON 结构。
        3.  **请注意：你的输出必须且只能是** 一个完整的、合法的 JSON 对象，不要包含任何额外说明或 Markdown 代码块标记 (如 ```json)。
        4.  如果某个字段信息在聊天记录中缺失，请用"无"或"未明确提及"填充。
        
        **字段定义 (Field Definitions):**
        -   **context**: 背景上下文（会话开始时的初始情景、正在处理的项目或代码库的概况）。
        -   **decision**: 关键决策（编码过程中做出的最核心技术选择或实现方案）。
        -   **userGoal**: 用户意图（用户最初希望实现的功能或解决的技术难题）。
        -   **results**: 执行结果（会话结束时，代码实现的状态和取得的进展）。
        -   **unsolved**: 未解决问题（会话结束时仍遗留的 Bug 或未完成的功能）。
        -   **plan**: 后续计划（根据对话推断下一步用户或 AI 可能会采取的行动或任务）。
        
        **输出格式要求 (JSON Schema):**
        
        {
          "context": "[在此处总结背景上下文]",
          "decision": "[在此处总结关键决策]",
          "userGoal": "[在此处总结用户意图]",
          "results": "[在此处总结执行结果]",
          "unsolved": "[在此处总结未解决问题]",
          "plan": "[在此处总结后续计划]"
        }
        
        **聊天记录内容:**
        {session_content}
        """;

    private final Project project;
    private final LlmClient llmClient;
    private static final Gson GSON = new Gson();

    public HistoryCompressor(Project project) {
        this.project = project;
        this.llmClient = project.getService(LlmClient.class);
    }

    /**
     * 异步压缩聊天记录，生成聊天元数据
     * @param chatSession 聊天会话
     * @return 包含聊天元数据的异步任务
     */
    public CompletableFuture<ChatMeta> compressAsync(ChatSession chatSession) {

        System.out.println("开始压缩聊天记录，Session ID: " + chatSession.getId());

        String prompt = buildPrompt(chatSession);

        CompletableFuture<String> responseFuture;
        try {
            responseFuture = llmClient.chatAsync(prompt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return responseFuture
                .thenApply(this::parseLlmResponse)
                .thenApply(meta -> {
                    System.out.println("成功压缩聊天记录，Session ID: " + chatSession.getId());
                    System.out.println("生成的 ChatMeta: " + GSON.toJson(meta));
                    return meta;
                })
                .exceptionally(ex -> {
                    System.out.println("LLM 压缩或解析过程中发生错误: " + ex.getMessage());
                    // 返回一个默认值或重新抛出更具体的异常
                    throw new RuntimeException("Chat compression failed", ex);
                });
    }

    /**
     * 构建压缩对话prompt
     * @param chatSession 聊天会话
     * @return 构建好的prompt字符串
     */
    public String buildPrompt(ChatSession chatSession) {

        return PROMPT_TEMPLATE.replace("{session_content}", chatSession.buildSessionContextPrompt());
    }

    /**
     * 解析llm返回的json为ChatMeta类
     */
    public ChatMeta parseLlmResponse(String response) {

        try {
            return GSON.fromJson(response, ChatMeta.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
