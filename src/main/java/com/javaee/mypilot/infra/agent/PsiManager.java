package com.javaee.mypilot.infra.agent;


import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * PSI 管理器，负责处理与 PSI（Program Structure Interface）相关的操作，
 * 包括代码解析、语法树管理等功能。
 * TODO: agent
 */
@Service(Service.Level.PROJECT)
public final class PsiManager {

    private final Project project;

    public PsiManager(Project project) {
        this.project = project;
    }

}
