package com.javaee.mypilot.view.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.javaee.mypilot.core.model.chat.CodeReference;
import com.javaee.mypilot.service.ManageService;
import org.jetbrains.annotations.NotNull;

/**
 * 添加代码引用到聊天的 Action
 * 用户选中代码后，通过右键菜单或快捷键将代码添加到聊天引用区
 */
public class AddCodeReferenceAction extends AnAction {
    
    public AddCodeReferenceAction() {
        super("添加到 MyPilot 聊天", "将选中的代码添加到 MyPilot 聊天引用区", null);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取项目和编辑器
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        if (project == null || editor == null || psiFile == null) {
            return;
        }
        
        // 获取选中的内容
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }
        
        // 创建 CodeReference
        CodeReference codeReference = new CodeReference();
        
        // 设置文件信息
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            codeReference.setVirtualFileUrl(virtualFile.getUrl());
        }
        
        // 设置选中代码的偏移量和行号
        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();
        codeReference.setStartOffset(startOffset);
        codeReference.setEndOffset(endOffset);
        
        // 计算行号
        int startLine = editor.getDocument().getLineNumber(startOffset) + 1; // 行号从1开始
        int endLine = editor.getDocument().getLineNumber(endOffset) + 1;
        codeReference.setStartLine(startLine);
        codeReference.setEndLine(endLine);
        
        // 设置选中的代码文本
        codeReference.setSelectedCode(selectedText);
        
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
    public void update(@NotNull AnActionEvent e) {
        // 获取项目和编辑器
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        // 始终显示菜单项
        e.getPresentation().setVisible(true);
        
        // 只有在有选中文本时才启用
        boolean hasSelection = false;
        if (project != null && editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();
            hasSelection = selectionModel.hasSelection() && 
                          selectionModel.getSelectedText() != null &&
                          !selectionModel.getSelectedText().trim().isEmpty();
        }
        
        e.getPresentation().setEnabled(hasSelection);
    }
}

