package com.javaee.mypilot.infra.api;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * 大语言模型客户端，负责与大语言模型进行交互，发送请求并接收响应。
 * TODO : rag
 */
@Service(Service.Level.PROJECT)
public final class LlmClient {

    private final Project project;

    public LlmClient(Project project) {
        this.project = project;
    }
}
