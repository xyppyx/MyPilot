package com.javaee.mypilot.infra.agent;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopup;
import com.javaee.mypilot.core.enums.CodeOpt;
import com.javaee.mypilot.core.model.agent.CodeAction;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * 代码差异管理器，负责处理代码差异的计算和展示。
 * 使用 IntelliJ Platform 的 Diff API 和异步任务执行器
 */
@Service(Service.Level.PROJECT)
public final class DiffManager {

    private final Project project;
    private final DiffContentFactory diffContentFactory;

    public DiffManager(Project project) {
        this.project = project;
        this.diffContentFactory = DiffContentFactory.getInstance();
    }

    /**
     * 处理代码变更并异步展示差异对比
     * 使用 IntelliJ Platform 的异步任务执行器
     * 此方法在 AgentService 中被调用
     * 同一个文件的多个修改会合并到一个diff窗口中显示
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

            // 按文件路径分组，同时过滤掉无效的操作
            Map<String, List<CodeAction>> actionsByFile = codeActions.stream()
                .filter(action -> action != null 
                        && action.getOpt() != null
                        && (action.getOpt() == CodeOpt.REPLACE || 
                             action.getOpt() == CodeOpt.INSERT || 
                             action.getOpt() == CodeOpt.DELETE)
                        && action.getFilePath() != null 
                        && !action.getFilePath().trim().isEmpty())
                .collect(Collectors.groupingBy(action -> {
                    String path = normalizeFilePath(action.getFilePath());
                    return path != null && !path.trim().isEmpty() ? path : "";
                }))
                .entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().trim().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            System.out.println("DiffManager: 按文件分组后，共 " + actionsByFile.size() + " 个文件需要显示diff");

            // 只有在有有效操作时才显示diff窗口
            if (actionsByFile.isEmpty()) {
                System.out.println("DiffManager: 没有有效的代码操作，跳过显示diff窗口");
                return;
            }

            // 为每个文件显示一个diff窗口
            for (Map.Entry<String, List<CodeAction>> entry : actionsByFile.entrySet()) {
                String filePath = entry.getKey();
                List<CodeAction> fileActions = entry.getValue();
                
                if (fileActions == null || fileActions.isEmpty()) {
                    continue;
                }
                
                System.out.println("DiffManager: 文件 " + filePath + " 有 " + fileActions.size() + " 个修改");
                
                // 按行号排序，确保修改顺序正确
                fileActions.sort(Comparator.comparingInt(CodeAction::getStartLine));
                
                // 显示该文件的所有修改
                _showDiffForFile(filePath, fileActions);
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    /**
     * 为单个代码操作显示差异对比窗口（公共方法）
     * 
     * @param action 代码操作
     */
    public void showDiffForAction(CodeAction action) {
        _showDiffForAction(action);
    }
    
    /**
     * 为单个文件的多个修改显示差异对比窗口
     * 
     * @param filePath 文件路径
     * @param actions 该文件的所有修改操作（已按行号排序）
     */
    private void _showDiffForFile(String filePath, List<CodeAction> actions) {
        // 文件查找操作在后台线程执行，避免在 EDT 上执行慢操作
        CompletableFuture.supplyAsync(() -> {
            try {
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                
                // 如果找不到，尝试在项目根目录下查找
                if ((file == null || !file.exists()) && project != null && project.getBasePath() != null) {
                    String projectBasePath = project.getBasePath();
                    String relativePath = filePath;
                    
                    // 如果路径是绝对路径，尝试提取相对路径部分
                    if (filePath.startsWith(projectBasePath)) {
                        relativePath = filePath.substring(projectBasePath.length());
                        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                            relativePath = relativePath.substring(1);
                        }
                    }
                    
                    // 尝试查找相对路径
                    VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                    if (baseDir != null) {
                        file = baseDir.findFileByRelativePath(relativePath);
                    }
                }
                
                return new AbstractMap.SimpleEntry<>(file, actions);
            } catch (Exception e) {
                System.err.println("查找文件时出错: " + e.getMessage());
                e.printStackTrace();
                return new AbstractMap.SimpleEntry<VirtualFile, List<CodeAction>>(null, actions);
            }
        }, AppExecutorUtil.getAppExecutorService()).thenAccept(entry -> {
            VirtualFile file = entry.getKey();
            List<CodeAction> fileActions = entry.getValue();
            
            // 在 EDT 上执行 UI 操作
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    if (file != null && file.exists()) {
                        Document document = FileDocumentManager.getInstance().getDocument(file);
                        
                        if (document != null) {
                            // 获取原始文件内容
                            String originalContent = document.getText();
                            
                            // 应用所有修改生成新内容
                            String modifiedContent = applyActionsToContent(originalContent, fileActions);
                            
                            // 创建diff内容：左侧使用文件绑定内容，右侧使用AI合成的新内容
                            DiffContent oldContent = diffContentFactory.create(project, file);
                            DiffContent newContent = diffContentFactory.create(modifiedContent, file.getFileType());
                            
                            // 创建差异请求，使用IntelliJ自带的diff工具
                            String title = String.format("AI代码修改建议: %s (%d 处修改)", 
                                file.getName(), fileActions.size());
                            SimpleDiffRequest request = new SimpleDiffRequest(
                                title,
                                oldContent,
                                newContent,
                                String.format("当前代码: %s", file.getName()),
                                String.format("AI建议的修改 (%d 处)", fileActions.size())
                            );
                            // 右键与工具栏上下文动作：支持按类型选择并应用、以及一键应用全部
                            java.util.List<AnAction> contextActions = new java.util.ArrayList<>();

                            contextActions.add(new AnAction("选择并应用替换") {
                                @Override
                                public void actionPerformed(AnActionEvent e) {
                                    java.util.List<CodeAction> replaceActions = fileActions.stream()
                                            .filter(a -> a.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.REPLACE)
                                            .collect(java.util.stream.Collectors.toList());
                                    if (replaceActions.isEmpty()) return;
                                    java.util.List<String> items = new java.util.ArrayList<>();
                                    for (int i = 0; i < replaceActions.size(); i++) {
                                        CodeAction a = replaceActions.get(i);
                                        items.add("替换: 行 " + a.getStartLine() + "-" + a.getEndLine());
                                    }
                                    JBPopup popup = JBPopupFactory.getInstance()
                                            .createPopupChooserBuilder(items)
                                            .setTitle("选择要应用的替换")
                                            .setItemChosenCallback(selected -> {
                                                int idx = items.indexOf(selected);
                                                if (idx >= 0) {
                                                    applyCodeAction(replaceActions.get(idx));
                                                }
                                            })
                                            .createPopup();
                                    popup.showInFocusCenter();
                                }
                            });

                            contextActions.add(new AnAction("选择并应用插入") {
                                @Override
                                public void actionPerformed(AnActionEvent e) {
                                    java.util.List<CodeAction> insertActions = fileActions.stream()
                                            .filter(a -> a.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.INSERT)
                                            .collect(java.util.stream.Collectors.toList());
                                    if (insertActions.isEmpty()) return;
                                    java.util.List<String> items = new java.util.ArrayList<>();
                                    for (int i = 0; i < insertActions.size(); i++) {
                                        CodeAction a = insertActions.get(i);
                                        items.add("插入: 行 " + a.getStartLine());
                                    }
                                    JBPopup popup = JBPopupFactory.getInstance()
                                            .createPopupChooserBuilder(items)
                                            .setTitle("选择要应用的插入")
                                            .setItemChosenCallback(selected -> {
                                                int idx = items.indexOf(selected);
                                                if (idx >= 0) {
                                                    applyCodeAction(insertActions.get(idx));
                                                }
                                            })
                                            .createPopup();
                                    popup.showInFocusCenter();
                                }
                            });

                            contextActions.add(new AnAction("选择并应用删除") {
                                @Override
                                public void actionPerformed(AnActionEvent e) {
                                    java.util.List<CodeAction> deleteActions = fileActions.stream()
                                            .filter(a -> a.getOpt() == com.javaee.mypilot.core.enums.CodeOpt.DELETE)
                                            .collect(java.util.stream.Collectors.toList());
                                    if (deleteActions.isEmpty()) return;
                                    java.util.List<String> items = new java.util.ArrayList<>();
                                    for (int i = 0; i < deleteActions.size(); i++) {
                                        CodeAction a = deleteActions.get(i);
                                        items.add("删除: 行 " + a.getStartLine() + "-" + a.getEndLine());
                                    }
                                    JBPopup popup = JBPopupFactory.getInstance()
                                            .createPopupChooserBuilder(items)
                                            .setTitle("选择要应用的删除")
                                            .setItemChosenCallback(selected -> {
                                                int idx = items.indexOf(selected);
                                                if (idx >= 0) {
                                                    applyCodeAction(deleteActions.get(idx));
                                                }
                                            })
                                            .createPopup();
                                    popup.showInFocusCenter();
                                }
                            });

                            contextActions.add(new AnAction("应用全部修改") {
                                @Override
                                public void actionPerformed(AnActionEvent e) {
                                    applyCodeActions(fileActions);
                                }
                            });

                            request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, contextActions);
                            
                            // 显示差异对比窗口
                            com.intellij.diff.DiffManager.getInstance().showDiff(project, request);
                        }
                    } else {
                        // 文件不存在，可能是新建文件的情况
                        if (!fileActions.isEmpty()) {
                            showDiffForNewFile(fileActions.get(0));
                        }
                    }
                } catch (Exception e) {
                    // 错误处理
                    System.err.println("显示代码差异时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }, ModalityState.nonModal());
        });
    }
    
    /**
     * 将多个修改操作应用到文件内容上，生成修改后的内容
     * 
     * @param originalContent 原始文件内容
     * @param actions 修改操作列表（已按行号排序）
     * @return 修改后的文件内容
     */
    private String applyActionsToContent(String originalContent, List<CodeAction> actions) {
        // 将内容按行分割
        List<String> lines = new ArrayList<>(Arrays.asList(originalContent.split("\n", -1)));
        
        // 从后往前应用修改，避免行号变化的影响
        for (int i = actions.size() - 1; i >= 0; i--) {
            CodeAction action = actions.get(i);
            CodeOpt opt = action.getOpt();
            int startLine = action.getStartLine();
            int endLine = action.getEndLine();
            
            if (opt == CodeOpt.REPLACE) {
                // 替换操作
                if (startLine >= 1 && endLine >= startLine && startLine <= lines.size()) {
                    // 删除旧行
                    int startIdx = startLine - 1;
                    int endIdx = Math.min(endLine - 1, lines.size() - 1);
                    
                    // 准备新内容
                    String newCode = action.getNewCode() != null ? action.getNewCode() : "";
                    List<String> newLines = new ArrayList<>(Arrays.asList(newCode.split("\n", -1)));
                    
                    // 如果最后一行是空的，可能是因为原文件最后没有换行
                    if (endIdx < lines.size() - 1 || originalContent.endsWith("\n")) {
                        // 保留换行
                    } else {
                        // 移除最后一个空行（如果存在）
                        if (!newLines.isEmpty() && newLines.get(newLines.size() - 1).isEmpty()) {
                            newLines.remove(newLines.size() - 1);
                        }
                    }
                    
                    // 替换行
                    lines.subList(startIdx, endIdx + 1).clear();
                    lines.addAll(startIdx, newLines);
                }
            } else if (opt == CodeOpt.INSERT) {
                // 插入操作：在指定行后插入
                if (startLine >= 1) {
                    int insertIdx = startLine < lines.size() ? startLine : lines.size();
                    String newCode = action.getNewCode() != null ? action.getNewCode() : "";
                    List<String> newLines = new ArrayList<>(Arrays.asList(newCode.split("\n", -1)));
                    lines.addAll(insertIdx, newLines);
                }
            } else if (opt == CodeOpt.DELETE) {
                // 删除操作
                if (startLine >= 1 && endLine >= startLine && startLine <= lines.size()) {
                    int startIdx = startLine - 1;
                    int endIdx = Math.min(endLine - 1, lines.size() - 1);
                    lines.subList(startIdx, endIdx + 1).clear();
                }
            }
        }
        
        // 重新组合内容
        return String.join("\n", lines);
    }
    
    /**
     * 为单个代码操作显示差异对比窗口（内部方法，保留用于向后兼容）
     * 
     * @param action 代码操作
     */
    private void _showDiffForAction(CodeAction action) {
        // 文件查找操作在后台线程执行，避免在 EDT 上执行慢操作
        CompletableFuture.supplyAsync(() -> {
            try {
                // 规范化文件路径（去除 file:// 前缀等）
                String normalizedPath = normalizeFilePath(action.getFilePath());
                VirtualFile file = LocalFileSystem.getInstance().findFileByPath(normalizedPath);
                
                // 如果找不到，尝试在项目根目录下查找
                if ((file == null || !file.exists()) && project != null && project.getBasePath() != null) {
                    String projectBasePath = project.getBasePath();
                    String relativePath = normalizedPath;
                    
                    // 如果路径是绝对路径，尝试提取相对路径部分
                    if (normalizedPath.startsWith(projectBasePath)) {
                        relativePath = normalizedPath.substring(projectBasePath.length());
                        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                            relativePath = relativePath.substring(1);
                        }
                    }
                    
                    // 尝试查找相对路径
                    VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                    if (baseDir != null) {
                        file = baseDir.findFileByRelativePath(relativePath);
                    }
                }
                
                return file;
            } catch (Exception e) {
                System.err.println("查找文件时出错: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, AppExecutorUtil.getAppExecutorService()).thenAccept(file -> {
            // 在 EDT 上执行 UI 操作
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    if (file != null && file.exists()) {
                        Document document = FileDocumentManager.getInstance().getDocument(file);
                        
                        if (document != null) {
                            // 创建旧内容（当前文件内容或指定的旧代码）
                            String oldText = action.getOldCode() != null ? action.getOldCode() : document.getText();
                            DocumentContent oldContent = diffContentFactory.create(oldText, file.getFileType());
                            
                            // 创建新内容（建议的修改）
                            String newText = action.getNewCode() != null ? action.getNewCode() : "";
                            DocumentContent newContent = diffContentFactory.create(newText, file.getFileType());
                            
                            // 创建差异请求，使用IntelliJ自带的diff工具
                            String title = String.format("AI代码修改建议: %s (行 %d-%d)", 
                                file.getName(), action.getStartLine(), action.getEndLine());
                            SimpleDiffRequest request = new SimpleDiffRequest(
                                title,
                                oldContent,
                                newContent,
                                String.format("当前代码: %s (行 %d-%d)", file.getName(), action.getStartLine(), action.getEndLine()),
                                "AI建议的修改"
                            );
                            
                            // 显示差异对比窗口（IntelliJ会自动显示应用/拒绝按钮）
                            com.intellij.diff.DiffManager.getInstance().showDiff(project, request);
                        }
                    } else {
                        // 文件不存在，可能是新建文件的情况
                        showDiffForNewFile(action);
                    }
                } catch (Exception e) {
                    // 错误处理
                    System.err.println("显示代码差异时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }, ModalityState.nonModal());
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
                String title = String.format("AI生成建议: %s", action.getFilePath());
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
            chain = chain.thenRunAsync(() -> _showDiffForAction(action), 
                AppExecutorUtil.getAppExecutorService());
        }
        
        if (callback != null) {
            chain.thenRun(callback);
        }
    }
    
    /**
     * 应用代码更改到文件
     * 
     * @param action 代码操作
     * @return 是否成功应用
     */
    public boolean applyCodeAction(CodeAction action) {
        if (action == null) {
            return false;
        }
        
        try {
            // 规范化文件路径（去除 file:// 前缀等）
            String normalizedPath = normalizeFilePath(action.getFilePath());
            
            // 尝试多种方式查找文件
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(normalizedPath);
            
            // 如果找不到，尝试使用原始路径（可能是相对路径）
            if (file == null || !file.exists()) {
                // 尝试在项目根目录下查找
                if (project != null && project.getBasePath() != null) {
                    String projectBasePath = project.getBasePath();
                    String relativePath = normalizedPath;
                    
                    // 如果路径是绝对路径，尝试提取相对路径部分
                    if (normalizedPath.startsWith(projectBasePath)) {
                        relativePath = normalizedPath.substring(projectBasePath.length());
                        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                            relativePath = relativePath.substring(1);
                        }
                    }
                    
                    // 尝试查找相对路径
                    VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
                    if (baseDir != null) {
                        file = baseDir.findFileByRelativePath(relativePath);
                    }
                }
            }
            
            if (file == null || !file.exists()) {
                System.err.println("DiffManager: 无法找到文件: " + action.getFilePath() + " (规范化后: " + normalizedPath + ")");
                System.err.println("DiffManager: 如果这是新建文件，请先创建文件后再应用更改");
                return false;
            }
            
            final VirtualFile finalFile = file; // 使变量final以便在lambda中使用
            Document document = FileDocumentManager.getInstance().getDocument(finalFile);
            if (document == null) {
                System.err.println("DiffManager: 无法获取文档: " + finalFile.getPath());
                return false;
            }
            
            // 使用WriteCommandAction确保撤销/重做功能正常工作
            return WriteCommandAction.writeCommandAction(project).compute(() -> {
                try {
                    CodeOpt opt = action.getOpt();
                    String newCode = action.getNewCode() != null ? action.getNewCode() : "";
                    
                    if (opt == CodeOpt.REPLACE) {
                        // 替换操作：替换指定行范围的代码
                        int startLine = action.getStartLine();
                        int endLine = action.getEndLine();
                        
                        if (startLine < 1 || endLine < startLine) {
                            System.err.println("DiffManager: 无效的行号范围: " + startLine + "-" + endLine);
                            return false;
                        }
                        
                        // 确保行号在有效范围内
                        int documentLineCount = document.getLineCount();
                        if (startLine > documentLineCount) {
                            System.err.println("DiffManager: 起始行号超出文档范围: " + startLine + " (文档总行数: " + documentLineCount + ")");
                            return false;
                        }
                        
                        int startOffset = document.getLineStartOffset(startLine - 1);
                        int endOffset = document.getLineEndOffset(Math.min(endLine - 1, documentLineCount - 1));
                        
                        // 记录替换前的文本（用于调试）
                        String oldText = document.getText().substring(startOffset, endOffset);
                        System.out.println("DiffManager: 准备替换代码");
                        System.out.println("  - 文件: " + finalFile.getPath());
                        System.out.println("  - 行号范围: " + startLine + "-" + endLine);
                        System.out.println("  - 偏移范围: " + startOffset + "-" + endOffset);
                        System.out.println("  - 旧代码长度: " + oldText.length());
                        System.out.println("  - 新代码长度: " + newCode.length());
                        
                        // 替换文本
                        document.replaceString(startOffset, endOffset, newCode);
                        
                        // 确保文档被保存
                        FileDocumentManager.getInstance().saveDocument(document);
                        
                        System.out.println("DiffManager: 成功替换代码 (行 " + startLine + "-" + endLine + ")，文档已保存");
                        return true;
                        
                    } else if (opt == CodeOpt.INSERT) {
                        // 插入操作：在指定行后插入代码
                        int insertLine = action.getStartLine();
                        
                        if (insertLine < 1) {
                            System.err.println("DiffManager: 无效的插入行号: " + insertLine);
                            return false;
                        }
                        
                        int insertOffset;
                        if (insertLine > document.getLineCount()) {
                            // 如果行号超出范围，在文件末尾插入
                            insertOffset = document.getTextLength();
                            if (insertOffset > 0 && !document.getText().endsWith("\n")) {
                                newCode = "\n" + newCode;
                            }
                        } else {
                            // 在指定行后插入
                            insertOffset = document.getLineEndOffset(insertLine - 1);
                            if (!newCode.startsWith("\n") && insertOffset > 0) {
                                newCode = "\n" + newCode;
                            }
                        }
                        
                        document.insertString(insertOffset, newCode);
                        
                        // 确保文档被保存
                        FileDocumentManager.getInstance().saveDocument(document);
                        
                        System.out.println("DiffManager: 成功插入代码 (行 " + insertLine + " 后)，文档已保存");
                        return true;
                        
                    } else if (opt == CodeOpt.DELETE) {
                        // 删除操作：删除指定行范围的代码
                        int startLine = action.getStartLine();
                        int endLine = action.getEndLine();
                        
                        if (startLine < 1 || endLine < startLine) {
                            System.err.println("DiffManager: 无效的行号范围: " + startLine + "-" + endLine);
                            return false;
                        }
                        
                        int startOffset = document.getLineStartOffset(startLine - 1);
                        int endOffset = document.getLineEndOffset(Math.min(endLine - 1, document.getLineCount() - 1));
                        
                        document.deleteString(startOffset, endOffset);
                        
                        // 确保文档被保存
                        FileDocumentManager.getInstance().saveDocument(document);
                        
                        System.out.println("DiffManager: 成功删除代码 (行 " + startLine + "-" + endLine + ")，文档已保存");
                        return true;
                        
                    } else {
                        System.err.println("DiffManager: 未知的操作类型: " + opt);
                        return false;
                    }
                } catch (Exception e) {
                    System.err.println("DiffManager: 应用代码更改时出错: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            });
            
        } catch (Exception e) {
            System.err.println("DiffManager: 应用代码更改失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 批量应用代码更改
     * 
     * @param codeActions 代码操作列表
     * @return 成功应用的数量
     */
    public int applyCodeActions(List<CodeAction> codeActions) {
        if (codeActions == null || codeActions.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        for (CodeAction action : codeActions) {
            if (applyCodeAction(action)) {
                successCount++;
            }
        }
        
        return successCount;
    }
    
    /**
     * 规范化文件路径
     * 去除 file:// 协议前缀，将 URL 转换为文件系统路径
     * 
     * @param filePath 原始文件路径（可能包含 file:// 前缀）
     * @return 规范化后的文件路径
     */
    private String normalizeFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }
        
        String originalPath = filePath;
        
        // 去除 file:// 前缀（支持 file:// 和 file:///）
        if (filePath.startsWith("file:///")) {
            filePath = filePath.substring(8); // file:/// -> 8个字符
        } else if (filePath.startsWith("file://")) {
            filePath = filePath.substring(7); // file:// -> 7个字符
        }
        
        // 在 Windows 上，如果路径以 / 开头（Unix风格），去除第一个斜杠
        // 例如：/D:/path/to/file -> D:/path/to/file
        if (filePath.length() > 2 && filePath.charAt(0) == '/' && 
            Character.isLetter(filePath.charAt(1)) && filePath.charAt(2) == ':') {
            filePath = filePath.substring(1);
        }
        
        // 处理 URL 编码的路径（将 %20 转换为空格等）
        try {
            // 如果路径包含 % 符号，说明可能有 URL 编码，使用 URI 解码
            if (filePath.contains("%")) {
                String uriString = originalPath.startsWith("file://") ? originalPath : "file:///" + filePath;
                java.net.URI uri = new java.net.URI(uriString);
                String decodedPath = uri.getPath();
                
                // 在 Windows 上处理盘符
                if (decodedPath != null && decodedPath.length() > 0) {
                    if (decodedPath.charAt(0) == '/' && decodedPath.length() > 2 && 
                        Character.isLetter(decodedPath.charAt(1)) && decodedPath.charAt(2) == ':') {
                        decodedPath = decodedPath.substring(1); // 去除开头的 /
                    }
                    filePath = decodedPath;
                }
            } else {
                // 如果没有 URL 编码，但路径可能丢失了盘符，尝试从原始路径恢复
                // 如果 filePath 不以盘符开头，但 originalPath 包含盘符，尝试恢复
                if (filePath.length() > 0 && filePath.charAt(0) != '/' && 
                    !(filePath.length() > 1 && Character.isLetter(filePath.charAt(0)) && filePath.charAt(1) == ':')) {
                    // 尝试从原始路径中提取盘符
                    if (originalPath.length() > 7) { // file://D:...
                        String afterProtocol = originalPath.substring(7); // 跳过 file://
                        if (afterProtocol.length() > 1 && Character.isLetter(afterProtocol.charAt(0)) && 
                            afterProtocol.charAt(1) == ':') {
                            // 找到了盘符，检查 filePath 是否需要添加
                            if (!filePath.startsWith(afterProtocol.substring(0, 2))) {
                                filePath = afterProtocol.charAt(0) + ":" + filePath;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 如果 URI 解析失败，继续使用已处理的路径
            System.err.println("规范化文件路径时出错: " + e.getMessage() + ", 原始路径: " + originalPath + ", 处理后: " + filePath);
            
            // 尝试手动修复丢失的盘符
            if (filePath.length() > 0 && filePath.charAt(0) == '/' && 
                originalPath.toLowerCase().startsWith("file://")) {
                String afterProtocol = originalPath.substring(7);
                if (afterProtocol.length() > 1 && Character.isLetter(afterProtocol.charAt(0)) && 
                    afterProtocol.charAt(1) == ':') {
                    filePath = afterProtocol.charAt(0) + ":" + filePath.substring(1);
                }
            }
        }
        
        // 统一路径分隔符（Windows 使用 \，Unix 使用 /）
        // IntelliJ 的 VirtualFile 应该能够处理两种格式，但为了保险起见，统一使用 /
        filePath = filePath.replace('\\', '/');
        
        System.out.println("DiffManager: 路径规范化 - 原始: " + originalPath + " -> 规范化: " + filePath);
        
        return filePath;
    }
}
