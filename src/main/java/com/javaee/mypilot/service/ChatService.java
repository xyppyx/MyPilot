package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.chat.CodeReference;
import com.javaee.mypilot.infra.AppExecutors;
import com.javaee.mypilot.infra.agent.PsiHandler;
import com.javaee.mypilot.infra.chat.HistoryCompressor;
import com.javaee.mypilot.infra.chat.TokenEvaluator;
import com.javaee.mypilot.infra.repo.IChatRepo;
import com.javaee.mypilot.infra.repo.InMemChatRepo;
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
     * 根据标题获取聊天会话
     * @param title 会话标题
     * @return 聊天会话，如果不存在则返回 null
     */
    public com.javaee.mypilot.core.model.chat.ChatSession getChatSessionByTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        
        return chatRepo.getAllChatSessions().stream()
                .filter(session -> title.equals(session.getTitle()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据标题删除聊天会话
     * @param title 会话标题
     * @return 是否删除成功
     */
    public boolean deleteChatSessionByTitle(String title) {
        return chatRepo.deleteChatSessionByTitle(title);
    }

    /**
     * 删除所有聊天会话
     * @return 删除的会话数量
     */
    public int deleteAllChatSessions() {
        return chatRepo.deleteAllChatSessions();
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

            // 1. 获取聊天会话
            ChatSession chatSession = chatRepo.getChatSession(sessionId);
            if (chatSession == null) {
                throw new IllegalArgumentException("Invalid session ID: " + sessionId);
            }
            System.out.println("step1.1: 成功获取聊天会话，Session ID: " + sessionId);

            // 2. 添加用户消息
            ChatMessage userMessage = new ChatMessage(ChatMessage.Type.USER, message);
            chatSession.addMessage(userMessage);
            System.out.println("step1.2: 成功添加用户消息，内容: " + message);

            // 3. 更新token消耗量 (CPU 密集型，保持同步)
            Integer token = tokenEvaluator.estimateTokens(userMessage.getContent());
            // 防御性编程：如果 tokenUsage 为 null，使用 0 作为默认值
            Integer currentUsage = chatSession.getTokenUsage();
            if (currentUsage == null) {
                currentUsage = 0;
            }
            chatSession.setTokenUsage(token + currentUsage);
            System.out.println("step1.3: 成功更新token消耗量，当前总量: " + chatSession.getTokenUsage());

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
            System.out.println("step2.1: 压缩任务已启动，Session ID: " + chatSession.getId());
            // 如果需要压缩，启动异步压缩任务
            compressionFuture = historyCompressor.compressAsync(chatSession)
                    .exceptionally(
                            ex -> {
                                // 处理压缩失败的情况（记录日志等）
                                System.err.println("History compression failed: " + ex.getMessage());
                                return null;
                            }
                    )
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
            System.out.println("step2全部完成");
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

        CompletableFuture<ChatMessage> responseFuture = switch (chatOpt) {
                case ASK -> RagService.handleRequestAsync(chatSession);
                case AGENT -> agentService.handleRequestAsync(chatSession);
        };

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

        System.out.println("开始处理聊天请求，Session ID: " + sessionId + ", ChatOpt: " + chatOpt);

        // 任务 1: 设置聊天会话
        return setupChatSessionAsync(sessionId, message)
                // 任务 2: 并发处理压缩和代码上下文
                .thenCompose(chatSession ->
                    handleCompressionAndCodeContextAsync(chatSession, codeReferences)
                )
                // 任务 3: 调用相应服务处理请求
                .thenCompose(chatSession ->
                    handleServiceRequestAsync(chatSession, chatOpt)
                )
                .thenApply(responseMessage -> {
                    System.out.println("完成聊天请求处理，Session ID: " + sessionId);
                    System.out.println("产生的回复消息: " + responseMessage.getContent());
                    return responseMessage;
                });
    }
}