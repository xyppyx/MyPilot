package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.infra.rag.DocumentProcessor;
import com.javaee.mypilot.infra.rag.KnowledgeIndexer;
import com.javaee.mypilot.infra.api.LlmClient;
import org.bouncycastle.asn1.dvcs.ServiceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * RAG服务类
 * TODO: rag
 */
@Service(Service.Level.PROJECT)
public final class RagService {

    private final Project project;
    private final DocumentProcessor documentProcessor;
    private final KnowledgeIndexer knowledgeIndexer;
    private final LlmClient llmClient;

    public RagService(@NotNull Project project) {
        this.project = project;
        this.documentProcessor = project.getService(DocumentProcessor.class);
        this.knowledgeIndexer = project.getService(KnowledgeIndexer.class);
        this.llmClient = project.getService(LlmClient.class);
    }

    /**
     * 处理请求
     * @param chatSession 聊天会话
     * @return llm回复
     */
    public ChatMessage handleRequest(ChatSession chatSession) {
        //TODO: rag
        return null;
    }
}
