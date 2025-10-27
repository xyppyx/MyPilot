package com.javaee.mypilot.view.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * MyPilot 工具窗口工厂
 * Tool window factory for creating the MyPilot chat interface
 */
public class ToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 创建聊天面板
        ChatPanel chatPanel = new ChatPanel(project);
        
        // 将面板添加到工具窗口
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(chatPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        // 所有项目都可用
        return true;
    }
}
