package com.javaee.mypilot.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.javaee.mypilot.core.model.chat.CodeReference;
import com.javaee.mypilot.service.ManageService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 添加文件夹代码引用到聊天的 Action
 * 用户右键文件夹后，递归遍历文件夹下的所有子文件，将每个文件的所有代码添加到聊天引用区
 */
public class AddDirectoryCodeReferenceAction extends AnAction {
    
    public AddDirectoryCodeReferenceAction() {
        super("添加文件夹代码到 MyPilot 聊天", "将文件夹下所有子文件的所有代码添加到 MyPilot 聊天引用区", null);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取项目和文件夹
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        
        if (project == null || virtualFile == null) {
            return;
        }
        
        // 只处理目录
        if (!virtualFile.isDirectory()) {
            return;
        }
        
        // 递归收集所有文件
        List<VirtualFile> allFiles = new ArrayList<>();
        collectFiles(virtualFile, allFiles);
        
        if (allFiles.isEmpty()) {
            return;
        }
        
        // 读取文件内容并创建 CodeReference
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        ManageService manageService = ManageService.getInstance(project);
        
        int successCount = 0;
        int failCount = 0;
        
        for (VirtualFile file : allFiles) {
            try {
                Document document = documentManager.getDocument(file);
                if (document == null) {
                    failCount++;
                    continue;
                }
                
                String fileContent = document.getText();
                if (fileContent == null || fileContent.trim().isEmpty()) {
                    failCount++;
                    continue;
                }
                
                // 计算文件的总行数
                int totalLines = document.getLineCount();
                int startLine = 1;
                int endLine = totalLines;
                
                // 计算文件的偏移量
                int startOffset = 0;
                int endOffset = document.getTextLength();
                
                // 创建 CodeReference
                CodeReference codeReference = new CodeReference();
                codeReference.setVirtualFileUrl(file.getUrl());
                codeReference.setStartOffset(startOffset);
                codeReference.setEndOffset(endOffset);
                codeReference.setStartLine(startLine);
                codeReference.setEndLine(endLine);
                codeReference.setSelectedCode(fileContent);
                
                // 添加到 ManageService
                manageService.addCodeReference(codeReference);
                successCount++;
            } catch (Exception ex) {
                failCount++;
                // 继续处理其他文件，不中断整个过程
            }
        }
        
        // 自动打开 MyPilot 工具窗口
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("MyPilot");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.activate(null);
        }
        
        // 显示处理结果提示（可选）
        if (failCount > 0) {
            Messages.showInfoMessage(
                project,
                String.format("成功添加 %d 个文件，%d 个文件处理失败", successCount, failCount),
                "添加文件夹代码完成"
            );
        }
    }
    
    /**
     * 递归收集文件夹下的所有文件
     * @param directory 目录
     * @param result 结果列表
     */
    private void collectFiles(VirtualFile directory, List<VirtualFile> result) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        
        VirtualFile[] children = directory.getChildren();
        if (children == null) {
            return;
        }
        
        for (VirtualFile child : children) {
            if (child.isDirectory()) {
                // 递归处理子目录
                collectFiles(child, result);
            } else {
                // 添加文件到结果列表
                result.add(child);
            }
        }
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 获取项目和文件
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        
        // 只在有项目、有文件且是目录时启用
        boolean enabled = project != null && 
                         virtualFile != null && 
                         virtualFile.isDirectory();
        
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}

