package com.javaee.mypilot.infra.rag;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * 知识索引器，负责将处理后的文件内容进行索引，以便后续的知识检索和问答。
 * TODO: rag
 */
@Service(Service.Level.PROJECT)
public final class KnowledgeIndexer {

    private final Project project;

    public KnowledgeIndexer(Project project) {
        this.project = project;
    }
}
