package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.infra.agent.DiffManager;
import com.javaee.mypilot.infra.api.LlmClient;
import com.javaee.mypilot.infra.agent.PsiHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Agent服务类
 * TODO : agent
 */
@Service(Service.Level.PROJECT)
public final class AgentService {

    private final Project project;
    private final PsiHandler psiHandler;
    private final LlmClient llmClient;
    private final DiffManager diffManager;

    public AgentService(@NotNull Project project) {
        this.project = project;
        this.psiHandler = project.getService(PsiHandler.class);
        this.llmClient = project.getService(LlmClient.class);
        this.diffManager = project.getService(DiffManager.class);
    }

    /**
     * 处理请求
     * @param chatSession 聊天会话
     * @return llm回复
     */
    public ChatMessage handleRequest(ChatSession chatSession) {
        //TODO: agent
        return null;
    }
}
