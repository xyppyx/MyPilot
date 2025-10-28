package com.javaee.mypilot.infra.agent;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.agent.CodeAction;

import java.util.List;

/**
 * 代码差异管理器，负责处理代码差异的计算和展示。
 * TODO: idea
 * TODO: 使用idea diff api与idea platform 异步任务执行器
 */
@Service(Service.Level.PROJECT)
public final class DiffManager {

    private final Project project;

    public DiffManager(Project project) {
        this.project = project;
    }

    /**
     * TODO:idea, 注: 在agent service中被调用
     */
    public void handleCodeChanges(List<CodeAction> codeActions) {

    }
}
