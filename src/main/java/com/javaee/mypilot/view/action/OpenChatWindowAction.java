package com.javaee.mypilot.view.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * 打开 MyPilot 聊天窗口的 Action
 */
public class OpenChatWindowAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 获取 ToolWindowManager
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        
        // 获取 MyPilot 工具窗口
        ToolWindow toolWindow = toolWindowManager.getToolWindow("MyPilot");
        
        if (toolWindow != null) {
            // 激活并显示工具窗口
            toolWindow.activate(null);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有项目时启用
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}

