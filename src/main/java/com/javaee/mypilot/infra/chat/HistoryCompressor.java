package com.javaee.mypilot.infra.chat;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatMeta;

import java.util.List;

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

    public ChatMeta compress(List<ChatMessage> messages) {
        // TODO: agent
        return null;
    }
}
