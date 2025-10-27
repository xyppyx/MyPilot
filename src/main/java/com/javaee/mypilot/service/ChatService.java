package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.CodeContext;
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

    public ChatService(@NotNull Project project) {
        this.project = project;
        this.historyCompressor = project.getService(HistoryCompressor.class);
        this.tokenEvaluator = project.getService(TokenEvaluator.class);
        this.RagService = project.getService(RagService.class);
        this.agentService = project.getService(AgentService.class);
        this.psiHandler = project.getService(PsiHandler.class);
        this.chatRepo = project.getService(InMemChatRepo.class);
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
     * @param sessionId 聊天会话ID
     * @param chatOpt 聊天选项
     * @param message 用户请求内容
     * @param codeReferences 代码引用信息列表
     * @return 聊天回复消息
     */
    public CompletableFuture<ChatMessage> handleRequestAsync(String sessionId, ChatOpt chatOpt, String message, List<CodeReference> codeReferences) {

        // 步骤一: 获取聊天会话,添加用户消息,估算token消耗量
        CompletableFuture<ChatSession> setupFuture = CompletableFuture.supplyAsync(() -> {

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
            chatSession.setTokenUsage(token + chatSession.getTokenUsage());

            return chatSession;
        });

        // 步骤二: 并发 压缩对话 和 获取代码上下文
        CompletableFuture<ChatSession> contextAndCompressFuture = setupFuture.thenCompose(chatSession -> {

            // 任务 2A: 压缩对话 (仅在需要时执行)
            CompletableFuture<Void> compressionFuture;

            if (tokenEvaluator.isThresholdReached(chatSession.getTokenUsage())) {
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
            // 这是一个独立于压缩的异步任务，可以并发执行
            CompletableFuture<List<CodeContext>> codeContextFuture = CompletableFuture.supplyAsync(() ->
                    psiHandler.fetchCodeContext(codeReferences)
            );

            // 等待两个任务完成，然后更新 chatSession 并返回它
            return compressionFuture.thenCombine(codeContextFuture, (v, codeContexts) -> {
                // 两个 Future 都完成后执行
                chatSession.setCodeContexts(codeContexts);
                return chatSession;
            });
        });

        // 步骤三: 根据聊天选项调用相应的服务处理请求
        return contextAndCompressFuture.thenCompose(chatSession -> {

            // 任务 3A: 服务请求处理 (RAG 或 Agent) - 这是流程的核心和瓶颈，必须异步化
            CompletableFuture<ChatMessage> responseFuture = CompletableFuture.supplyAsync(() -> {
                // 核心服务调用
                return switch (chatOpt) {
                    case ASK -> RagService.handleRequest(chatSession); // 假设 handleRequest 是同步耗时方法
                    case AGENT -> agentService.handleRequest(chatSession); // 假设 handleRequest 是同步耗时方法
                    default -> throw new IllegalArgumentException("Unsupported chat option: " + chatOpt);
                };
            });

            // 任务 3B: 善后和保存
            return responseFuture.thenApply(responseMessage -> {
                // 副作用：更新会话状态并保存 (最好确保 chatRepo.saveChatSession 快速或异步)
                chatSession.addMessage(responseMessage);
                chatRepo.saveChatSession(chatSession);
                return responseMessage;
            });
        });
    }
}
