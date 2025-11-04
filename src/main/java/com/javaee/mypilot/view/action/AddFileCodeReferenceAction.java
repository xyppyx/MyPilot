package com.javaee.mypilot.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.javaee.mypilot.core.model.chat.CodeReference;
import com.javaee.mypilot.service.ManageService;
import org.jetbrains.annotations.NotNull;

/**
 * 添加文件代码引用到聊天的 Action
 * 用户右键文件后，将整个文件的所有代码添加到聊天引用区
 */
public class AddFileCodeReferenceAction extends AnAction {
    
    public AddFileCodeReferenceAction() {
        super("添加文件代码到 MyPilot 聊天", "将整个文件的所有代码添加到 MyPilot 聊天引用区", null);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取项目和文件
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        if (project == null || virtualFile == null) {
            return;
        }
        
        // 只处理文件，不处理目录
        if (virtualFile.isDirectory()) {
            return;
        }
        
        // 读取文件内容
        FileDocumentManager documentManager = FileDocumentManager.getInstance();
        Document document = documentManager.getDocument(virtualFile);
        
        if (document == null) {
            return;
        }
        
        // 获取文件的所有内容
        String fileContent = document.getText();
        
        if (fileContent == null || fileContent.trim().isEmpty()) {
            return;
        }
        
        // 计算文件的总行数
        int totalLines = document.getLineCount();
        int startLine = 1; // 从第1行开始
        int endLine = totalLines; // 到最后一行
        
        // 计算文件的偏移量
        int startOffset = 0;
        int endOffset = document.getTextLength();
        
        // 创建 CodeReference
        CodeReference codeReference = new CodeReference();
        codeReference.setVirtualFileUrl(virtualFile.getUrl());
        codeReference.setStartOffset(startOffset);
        codeReference.setEndOffset(endOffset);
        codeReference.setStartLine(startLine);
        codeReference.setEndLine(endLine);
        codeReference.setSelectedCode(fileContent);
        
        // 添加到 ManageService
        ManageService manageService = ManageService.getInstance(project);
        manageService.addCodeReference(codeReference);
        
        // 自动打开 MyPilot 工具窗口
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("MyPilot");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.activate(null);
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
        
        // 只在有项目、有文件且是文件（非目录）时启用
        boolean enabled = project != null && 
                         virtualFile != null && 
                         !virtualFile.isDirectory();
        
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}

