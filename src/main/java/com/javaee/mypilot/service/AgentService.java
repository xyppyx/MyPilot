package com.javaee.mypilot.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;
import com.javaee.mypilot.core.model.agent.AgentResponse;
import com.javaee.mypilot.core.model.agent.CodeAction;
import com.javaee.mypilot.core.model.agent.CodeActionTypeAdapter;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.infra.agent.DiffManager;
import com.javaee.mypilot.infra.api.AgentPrompt;
import com.javaee.mypilot.infra.api.LlmClient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Agent服务类
 * 负责处理与智能代理相关的业务逻辑，包括构建Prompt、调用大语言模型API以及解析响应等。
 */
@Service(Service.Level.PROJECT)
public final class AgentService {

    private final Project project;
    private final LlmClient llmClient;
    private final DiffManager diffManager;
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(CodeAction.class, new CodeActionTypeAdapter())
            .create();
    
    // 缓存最近的代码操作，以便用户可以应用它们
    private List<CodeAction> lastCodeActions = new ArrayList<>();

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
                        String formattedExplanation = formatExplanation(agentResponse.getExplanation());
                        ChatMessage responseMessage = new ChatMessage(ChatMessage.Type.ASSISTANT, formattedExplanation);
                        // 缓存代码操作，以便后续可以应用
                        lastCodeActions = agentResponse.getCodeActions() != null 
                                ? new ArrayList<>(agentResponse.getCodeActions())
                                : new ArrayList<>();
                        
                        // 异步处理代码变更
                        CompletableFuture.runAsync(() -> {
                            try {
                                var codeActions = agentResponse.getCodeActions();
                                if (codeActions != null && !codeActions.isEmpty()) {
                                    // 验证代码操作的有效性：过滤掉无效的操作
                                    List<CodeAction> validActions = codeActions.stream()
                                            .filter(action -> action != null 
                                                    && action.getOpt() != null
                                                    && (action.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.REPLACE 
                                                            || action.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.INSERT 
                                                            || action.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.DELETE)
                                                    && action.getFilePath() != null 
                                                    && !action.getFilePath().trim().isEmpty())
                                            .collect(Collectors.toList());
                                    
                                    if (!validActions.isEmpty()) {
                                        System.out.println("AgentService: 找到 " + validActions.size() + " 个有效的代码操作（共 " + codeActions.size() + " 个）");
                                        diffManager.handleCodeChanges(validActions);
                                    } else {
                                        System.out.println("AgentService: 没有有效的代码操作需要处理（共 " + codeActions.size() + " 个操作，但都不有效）");
                                    }
                                } else {
                                    System.out.println("AgentService: 没有代码操作需要处理");
                                }
                            } catch (Exception e) {
                                System.err.println("AgentService: 处理代码变更时出错: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
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
    
    /**
     * 获取最近的代码操作列表
     * @return 最近的代码操作列表，如果没有则返回空列表
     */
    @NotNull
    public List<CodeAction> getLastCodeActions() {
        return new ArrayList<>(lastCodeActions);
    }
    
    /**
     * 应用最近的代码更改
     * @return 成功应用的数量
     */
    public int applyLastCodeActions() {
        if (lastCodeActions.isEmpty()) {
            return 0;
        }
        return diffManager.applyCodeActions(lastCodeActions);
    }
    
    /**
     * 清除缓存的代码操作
     */
    public void clearLastCodeActions() {
        lastCodeActions.clear();
    }
    
    /**
     * 格式化解释文本，确保编号列表项之间有换行
     * 支持多种编号格式：1. 数字点号格式（1. 2. 3.）
     *                  2. 数字括号格式（1) 2) 3)）
     *                  3. 分号分隔的列表
     * 
     * @param explanation 原始解释文本
     * @return 格式化后的文本
     */
    private String formatExplanation(String explanation) {
        if (explanation == null || explanation.trim().isEmpty()) {
            return explanation;
        }
        
        String formatted = explanation;
        
        // 步骤1: 处理冒号后直接跟着编号的情况（在冒号后换行）
        // 匹配 ":数字)" 或 "：数字)" 或 ":数字." 或 "：数字."，在冒号后添加换行
        formatted = formatted.replaceAll("([:：])\\s*(\\d+)([.)])", "$1\n$2$3");
        
        // 步骤2: 处理分号分隔的编号列表（支持数字点号和数字括号两种格式）
        // 将 ";数字." 或 "；数字." 或 ";数字)" 或 "；数字)" 替换为 ";\n数字." 或 "；\n数字)" 等
        formatted = formatted.replaceAll("([;；])\\s*(\\d+)([.)])", "$1\n$2$3");
        
        // 步骤3: 处理空格分隔的编号列表（统一处理点号和括号格式）
        // 匹配非换行符、非冒号、非分号 + 一个或多个空格 + 数字 + 点号或右括号，在数字前添加换行
        formatted = Pattern.compile("([^\\n\\r:：;；])(\\s+)(\\d+)([.)])", Pattern.MULTILINE)
                .matcher(formatted)
                .replaceAll("$1\n$3$4");
        
        return formatted;
    }
}
