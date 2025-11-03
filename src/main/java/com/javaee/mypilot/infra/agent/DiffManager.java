package com.javaee.mypilot.infra.agent;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.javaee.mypilot.core.enums.CodeOpt;
import com.javaee.mypilot.core.model.agent.CodeAction;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 代码差异管理器，负责处理代码差异的计算和展示。
 * 使用 IntelliJ Platform 的 Diff API 和异步任务执行器
 */
@Service(Service.Level.PROJECT)
public final class DiffManager {

    private final Project project;
    private final DiffContentFactory diffContentFactory;
    private final DiffRequestFactory diffRequestFactory;

    public DiffManager(Project project) {
        this.project = project;
        this.diffContentFactory = DiffContentFactory.getInstance();
        this.diffRequestFactory = DiffRequestFactory.getInstance();
    }

    /**
     * 处理代码变更并异步展示差异对比
     * 使用 IntelliJ Platform 的异步任务执行器
     * 此方法在 AgentService 中被调用
     * 
     * @param codeActions LLM 返回的代码操作列表
     * @return 异步任务，完成时返回处理结果
     */
    public CompletableFuture<Void> handleCodeChanges(List<CodeAction> codeActions) {
        if (codeActions == null || codeActions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 使用 IntelliJ Platform 的线程池异步执行
        return CompletableFuture.runAsync(() -> {
            System.out.println("处理代码变更，操作数量: " + codeActions.size());

            for (CodeAction action : codeActions) {

                System.out.println("处理代码操作: " + action);
                if (action == null) continue;
                
                // 根据不同的操作类型处理
                if (action.getOpt() == CodeOpt.REPLACE || action.getOpt() == CodeOpt.INSERT) {
                    showDiffForAction(action);
                } else if (action.getOpt() == CodeOpt.DELETE) {
                    // 删除操作也可以显示差异
                    showDiffForAction(action);
                }
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    /**
     * 为单个代码操作显示差异对比窗口
     * 
     * @param action 代码操作
     */
    private void showDiffForAction(CodeAction action) {
        // 在 EDT (Event Dispatch Thread) 上执行 UI 操作
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(action.getFilePath());
                
                if (file != null && file.exists()) {
                    Document document = FileDocumentManager.getInstance().getDocument(file);
                    
                    if (document != null) {
                        // 创建旧内容（当前文件内容或指定的旧代码）
                        String oldText = action.getOldCode() != null ? action.getOldCode() : document.getText();
                        DocumentContent oldContent = diffContentFactory.create(oldText, file.getFileType());
                        
                        // 创建新内容（建议的修改）
                        String newText = action.getNewCode() != null ? action.getNewCode() : "";
                        DocumentContent newContent = diffContentFactory.create(newText, file.getFileType());
                        
                        // 创建差异请求
                        String title = String.format("代码变更建议: %s (行 %d-%d)", 
                            file.getName(), action.getStartLine(), action.getEndLine());
                        SimpleDiffRequest request = new SimpleDiffRequest(
                            title,
                            oldContent,
                            newContent,
                            "当前代码",
                            "建议修改"
                        );
                        
                        // 显示差异对比窗口
                        com.intellij.diff.DiffManager.getInstance().showDiff(project, request);
                    }
                } else {
                    // 文件不存在，可能是新建文件的情况
                    showDiffForNewFile(action);
                }
            } catch (Exception e) {
                // 错误处理
                System.err.println("显示代码差异时出错: " + e.getMessage());
            }
        });
    }

    /**
     * 为新建文件显示差异对比
     * 
     * @param action 代码操作
     */
    private void showDiffForNewFile(CodeAction action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // 创建空内容作为旧内容
                DocumentContent oldContent = diffContentFactory.create("");
                
                // 创建新内容
                String newText = action.getNewCode() != null ? action.getNewCode() : "";
                DocumentContent newContent = diffContentFactory.create(newText);
                
                // 创建差异请求
                String title = String.format("新建文件建议: %s", action.getFilePath());
                SimpleDiffRequest request = new SimpleDiffRequest(
                    title,
                    oldContent,
                    newContent,
                    "空文件",
                    "建议内容"
                );
                
                // 显示差异对比窗口
                com.intellij.diff.DiffManager.getInstance().showDiff(project, request);
            } catch (Exception e) {
                System.err.println("显示新文件差异时出错: " + e.getMessage());
            }
        });
    }

    /**
     * 批量处理多个代码操作并顺序展示差异
     * 
     * @param codeActions 代码操作列表
     * @param callback 每个差异展示完成后的回调
     */
    public void handleCodeChangesSequentially(List<CodeAction> codeActions, Runnable callback) {
        if (codeActions == null || codeActions.isEmpty()) {
            if (callback != null) callback.run();
            return;
        }

        // 使用异步任务按顺序处理
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        
        for (CodeAction action : codeActions) {
            chain = chain.thenRunAsync(() -> showDiffForAction(action), 
                AppExecutorUtil.getAppExecutorService());
        }
        
        if (callback != null) {
            chain.thenRun(callback);
        }
    }
}
