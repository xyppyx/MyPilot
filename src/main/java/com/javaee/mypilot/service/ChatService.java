package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.CodeContext;
import com.javaee.mypilot.infra.chat.HistoryCompressor;
import com.javaee.mypilot.infra.chat.TokenEvaluator;
import com.javaee.mypilot.infra.repo.IChatRepo;
import com.javaee.mypilot.infra.repo.InMemChatRepo;
import com.javaee.mypilot.core.model.chat.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    private final IChatRepo chatRepo;

    public ChatService(@NotNull Project project) {
        this.project = project;
        this.historyCompressor = project.getService(HistoryCompressor.class);
        this.tokenEvaluator = project.getService(TokenEvaluator.class);
        this.RagService = project.getService(RagService.class);
        this.agentService = project.getService(AgentService.class);
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
     * 处理用户的聊天消息
     * @param sessionId 聊天会话ID
     * @param chatOpt 聊天选项
     * @param message 用户请求内容
     * @param codeContext 代码上下文
     * @return 聊天回复消息
     */
    public ChatMessage handleRequest(String sessionId, ChatOpt chatOpt, String message, CodeContext codeContext) {

        // 获取聊天会话
        ChatSession chatSession = chatRepo.getChatSession(sessionId);
        if (chatSession == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }

        // 添加用户消息到会话
        ChatMessage userMessage = new ChatMessage(ChatMessage.Type.USER, message);
        chatSession.addMessage(userMessage);

        // 更新token消耗量
        Integer token = tokenEvaluator.estimateTokens(userMessage.getContent());
        chatSession.setTokenUsage(token + chatSession.getTokenUsage());

        // 判断是否压缩历史记录
        if (tokenEvaluator.isThresholdReached(chatSession.getTokenUsage())) {
            ChatMeta compressedHistory = historyCompressor.compress(chatSession.getMessages());
            chatSession.setMeta(compressedHistory);
            chatSession.setOffset(chatSession.getMessageCount());
        }

        // 根据聊天选项调用相应的服务处理请求
        ChatMessage responseMessage = switch (chatOpt) {
            case ASK -> RagService.handleRequest(chatSession);
            case AGENT -> agentService.handleRequest(chatSession);
            default -> throw new IllegalArgumentException("Unsupported chat option: " + chatOpt);
        };

        chatSession.addMessage(responseMessage);
        chatRepo.saveChatSession(chatSession);

        return responseMessage;
    }
}
