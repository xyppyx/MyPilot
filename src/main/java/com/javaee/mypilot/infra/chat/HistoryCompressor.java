package com.javaee.mypilot.infra.chat;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatMeta;
import com.javaee.mypilot.core.model.chat.ChatSession;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天记录压缩器，负责对聊天记录进行压缩和摘要，以便节省存储空间和提高检索效率。
 * TODO: agent
 */
@Service(Service.Level.PROJECT)
public final class HistoryCompressor {

    private final Project project;

    public HistoryCompressor(Project project) {
        this.project = project;
    }

    /**
     * 压缩聊天记录，生成聊天元数据
     * @param chatSession 聊天会话
     * @return 聊天元数据(ChatMeta)
     */
    public ChatMeta compress(ChatSession chatSession) {
        // TODO: agent
        return null;
    }

    /**
     * 异步压缩聊天记录，生成聊天元数据
     * @param chatSession 聊天会话
     * @return 包含聊天元数据的异步任务
     */
    public CompletableFuture<ChatMeta> compressAsync(ChatSession chatSession) {
        return CompletableFuture.supplyAsync(() -> compress(chatSession));
    }
}
