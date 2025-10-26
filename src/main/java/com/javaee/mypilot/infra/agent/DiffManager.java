package com.javaee.mypilot.infra.agent;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * 代码差异管理器，负责处理代码差异的计算和展示。
 * TODO: idea
 */
@Service(Service.Level.PROJECT)
public final class DiffManager {

    private final Project project;

    public DiffManager(Project project) {
        this.project = project;
    }
}
