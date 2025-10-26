package com.javaee.mypilot.infra.rag;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * 文件处理器，负责对文件进行预处理、分割等操作，以便后续的知识索引和检索。
 * TODO: rag
 */
@Service(Service.Level.PROJECT)
public final class DocumentProcessor {

    private final Project project;

    public DocumentProcessor(com.intellij.openapi.project.Project project) {
        this.project = project;
    }
}
