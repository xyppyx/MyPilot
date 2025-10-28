package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.infra.AppExecutors;
import com.javaee.mypilot.infra.agent.PsiHandler;
import com.javaee.mypilot.infra.chat.HistoryCompressor;
import com.javaee.mypilot.infra.chat.TokenEvaluator;
import com.javaee.mypilot.infra.repo.IChatRepo;
import com.javaee.mypilot.infra.repo.InMemChatRepo;
import com.javaee.mypilot.core.model.chat.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天服务类
 * 负责处理用户的聊天消息，管理聊天历史记录，并与RAG服务和Agent服务交互。
 */
@Service(Service.Level.PROJECT)
public final class ChatService {

    private final Project project;
    private final HistoryCompressor historyCompressor;
    private final TokenEvaluator tokenEvaluator;
    private final RagService RagService;
    private final AgentService agentService;
    private final PsiHandler psiHandler;
    private final IChatRepo chatRepo;
    private final AppExecutors appExecutors;

    public ChatService(@NotNull Project project) {
        this.project = project;
        this.historyCompressor = project.getService(HistoryCompressor.class);
        this.tokenEvaluator = project.getService(TokenEvaluator.class);
        this.RagService = project.getService(RagService.class);
        this.agentService = project.getService(AgentService.class);
        this.psiHandler = project.getService(PsiHandler.class);
        this.chatRepo = project.getService(InMemChatRepo.class);
        this.appExecutors = project.getService(AppExecutors.class);
    }

    public static ChatService getInstance(Project project) {
        return project.getService(ChatService.class);
    }

    /**
     * 展示所有聊天记录标题
     * @return 聊天记录标题列表
     */
    public List<String> showAllChatTitles() {

        return chatRepo.getAllChatSessionTitles();
    }

    /**
     * 开始一个新的聊天会话
     * @return 新的聊天会话ID
     */
    public String startNewChatSession() {

        ChatSession chatSession = new ChatSession();
        chatRepo.saveChatSession(chatSession);
        return chatSession.getId();
    }

    /**
     * 异步处理用户的聊天消息
     * 获取聊天会话,添加用户消息,估算并更新token消耗量
     * @param sessionId 聊天会话ID
     * @param message 用户请求内容
     * @return 包含更新后聊天会话的异步任务
     */
    private CompletableFuture<ChatSession> setupChatSessionAsync(String sessionId, String message) {

        return CompletableFuture.supplyAsync(() -> {

            // 1. 获取聊天会话 (chatRepo.getChatSession 如果是耗时I/O，最好单独包装或使用异步API)
            ChatSession chatSession = chatRepo.getChatSession(sessionId);
            if (chatSession == null) {
                throw new IllegalArgumentException("Invalid session ID: " + sessionId);
            }

            // 2. 添加用户消息
            ChatMessage userMessage = new ChatMessage(ChatMessage.Type.USER, message);
            chatSession.addMessage(userMessage);

            // 3. 更新token消耗量 (CPU 密集型，保持同步)
            Integer token = tokenEvaluator.estimateTokens(userMessage.getContent());
            // 防御性编程：如果 tokenUsage 为 null，使用 0 作为默认值
            Integer currentUsage = chatSession.getTokenUsage();
            if (currentUsage == null) {
                currentUsage = 0;
            }
            chatSession.setTokenUsage(token + currentUsage);

            return chatSession;
        }, appExecutors.getCpuExecutor());
    }

    /**
     * 并发处理聊天记录压缩和代码上下文获取
     * @param chatSession 聊天会话
     * @param codeReferences 代码引用信息列表
     * @return 包含更新后聊天会话的异步任务
     */
    private CompletableFuture<ChatSession> handleCompressionAndCodeContextAsync(ChatSession chatSession, List<CodeReference> codeReferences) {

        // 任务 2A: 压缩对话 (仅在需要时执行)
        CompletableFuture<Void> compressionFuture;

        // 防御性编程：如果 tokenUsage 为 null，使用 0 作为默认值
        Integer tokenUsage = chatSession.getTokenUsage();
        if (tokenUsage == null) {
            tokenUsage = 0;
        }

        if (tokenEvaluator.isThresholdReached(tokenUsage)) {
            // 如果需要压缩，启动异步压缩任务
            compressionFuture = historyCompressor.compressAsync(chatSession)
                    .thenAccept(compressedHistory -> {
                        // 副作用：更新 chatSession 状态（在 compressionFuture 的线程中执行）
                        chatSession.setMeta(compressedHistory);
                        chatSession.setOffset(chatSession.getMessageCount());
                    }).thenRun(() -> {}); // 确保返回 Void 类型
        } else {
            // 如果不需要压缩，立即返回一个已完成的 Future
            compressionFuture = CompletableFuture.completedFuture(null);
        }

        // 任务 2B: 获取代码上下文 (Code Context)
        CompletableFuture<List<CodeContext>> codeContextFuture = psiHandler.fetchCodeContextAsync(codeReferences);

        // 等待两个任务完成，然后更新 chatSession 并返回它
        return compressionFuture.thenCombine(codeContextFuture, (v, codeContexts) -> {
            // 两个 Future 都完成后执行
            chatSession.setCodeContexts(codeContexts);
            return chatSession;
        });
    }

    /**
     * 根据聊天选项调用相应的服务处理请求
     * @param chatSession 聊天会话
     * @param chatOpt 聊天选项
     * @return llm回复
     */
    private CompletableFuture<ChatMessage> handleServiceRequestAsync(ChatSession chatSession, ChatOpt chatOpt) {

        // 根据不同的聊天选项调用不同的服务
        CompletableFuture<ChatMessage> responseFuture;
        
        if (chatOpt == ChatOpt.ASK) {
            // RAG 服务是同步的，需要包装为异步
            responseFuture = CompletableFuture.supplyAsync(() -> 
                RagService.handleRequest(chatSession)
            );
        } else if (chatOpt == ChatOpt.AGENT) {
            // Agent 服务已经是异步的
            responseFuture = agentService.handleRequestAsync(chatSession);
        } else {
            responseFuture = CompletableFuture.failedFuture(
                new IllegalArgumentException("Unsupported chat option: " + chatOpt)
            );
        }

        // 任务 3B: 善后和保存
        return responseFuture.thenApply(responseMessage -> {
            chatSession.addMessage(responseMessage);
            chatRepo.saveChatSession(chatSession);
            return responseMessage;
        });
    }

    /**
     * 异步处理用户的聊天消息
     * @param sessionId 聊天会话ID
     * @param chatOpt 聊天选项
     * @param message 用户请求内容
     * @param codeReferences 代码引用信息列表
     * @return 聊天回复消息
     */
    public CompletableFuture<ChatMessage> handleRequestAsync(String sessionId, ChatOpt chatOpt, String message, List<CodeReference> codeReferences) {
        // 任务 1: 设置聊天会话
        return setupChatSessionAsync(sessionId, message)
                // 任务 2: 并发处理压缩和代码上下文
                .thenCompose(chatSession ->
                    handleCompressionAndCodeContextAsync(chatSession, codeReferences)
                )
                // 任务 3: 调用相应服务处理请求
                .thenCompose(chatSession ->
                    handleServiceRequestAsync(chatSession, chatOpt)
                );
    }
}