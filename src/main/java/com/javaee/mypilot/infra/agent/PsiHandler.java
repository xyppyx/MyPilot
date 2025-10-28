package com.javaee.mypilot.infra.agent;


import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.chat.CodeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * PSI 管理器，负责处理与 PSI（Program Structure Interface）相关的操作，
 * 包括代码解析、语法树管理等功能。
 */
@Service(Service.Level.PROJECT)
public final class PsiHandler {

    private final Project project;

    public PsiHandler(Project project) {
        this.project = project;
    }

    /**
     * TODO: idea
     * TODO: 改为idea platform 异步任务执行器
     * 异步根据代码引用列表，提取对应的代码上下文信息
     * @param codeReferences 代码引用列表
     * @return 包含代码上下文列表的异步任务
     */
    public CompletableFuture<List<CodeContext>> fetchCodeContextAsync(List<CodeReference> codeReferences) {
        return CompletableFuture.supplyAsync(() -> fetchCodeContext(codeReferences));
    }

    /**
     * 根据代码引用列表，提取对应的代码上下文信息
     * @param codeReferences 代码引用列表
     * @return 代码上下文列表
     */
    public List<CodeContext> fetchCodeContext(List<CodeReference> codeReferences) {

        List<CodeContext> contexts = new ArrayList<>();

        if (codeReferences == null || codeReferences.isEmpty()) {
            return contexts;
        }

        for (CodeReference ref : codeReferences) {
            if (ref == null) continue;

            CodeContext ctx = new CodeContext();
            ctx.setSourceReference(ref);

            String selected = ref.getSelectedCode();
            String vurl = ref.getVirtualFileUrl();

            try {
                VirtualFile vf = null;
                if (vurl != null && !vurl.isEmpty()) {
                    // 处理 URL 或路径
                    if (vurl.startsWith("file://") || vurl.contains("://")) {
                        vf = VirtualFileManager.getInstance().findFileByUrl(vurl);
                    } else {
                        vf = LocalFileSystem.getInstance().findFileByPath(vurl);
                    }
                }

                if (vf != null) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
                    if (psiFile != null) {
                        Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                        String fileText = psiFile.getText();

                        // 如果未提供选中代码，则根据偏移量提取
                        if ((selected == null || selected.isEmpty()) && ref.getStartOffset() >= 0 && ref.getEndOffset() > ref.getStartOffset()) {
                            int start = Math.max(0, ref.getStartOffset());
                            int end = Math.min(fileText.length(), ref.getEndOffset());
                            if (start < end && end <= fileText.length()) {
                                selected = fileText.substring(start, end);
                            }
                        }

                        // 提取上下文代码片段
                        if (doc != null) {
                            int startLine = ref.getStartLine();
                            int endLine = ref.getEndLine();
                            if (startLine <= 0 && ref.getStartOffset() >= 0) {
                                startLine = doc.getLineNumber(Math.max(0, ref.getStartOffset()));
                            }
                            if (endLine <= 0 && ref.getEndOffset() > 0) {
                                int off = Math.min(ref.getEndOffset(), Math.max(0, fileText.length() - 1));
                                endLine = doc.getLineNumber(off);
                            }

                            // 边界检查
                            if (startLine < 0) startLine = 0;
                            if (endLine < startLine) endLine = startLine;

                            int contextStartLine = Math.max(0, startLine - 5);
                            int contextEndLine = Math.min(doc.getLineCount() - 1, endLine + 5);

                            int contextStartOffset = doc.getLineStartOffset(contextStartLine);
                            int contextEndOffset = doc.getLineEndOffset(contextEndLine);

                            String surrounding = doc.getText().substring(contextStartOffset, contextEndOffset);
                            ctx.setSurroundingCode(surrounding);
                        } else {
                            // 无法获取 Document，使用整个文件内容作为上下文
                            ctx.setSurroundingCode(fileText);
                        }

                        ctx.setSelectedCode(selected);
                        ctx.setFileName(vf.getName());

                        // 获取 package 名称
                        PsiPackageStatement pkg = PsiTreeUtil.findChildOfType(psiFile, PsiPackageStatement.class);
                        if (pkg != null) {
                            ctx.setPackageName(pkg.getPackageName());
                        }

                        // 获取 class 和 method 名称
                        int elementOffset = Math.max(0, ref.getStartOffset());
                        if (elementOffset >= fileText.length()) elementOffset = Math.max(0, fileText.length() - 1);
                        PsiElement el = psiFile.findElementAt(elementOffset);
                        if (el != null) {
                            PsiClass cls = PsiTreeUtil.getParentOfType(el, PsiClass.class);
                            if (cls != null) ctx.setClassName(cls.getName());

                            PsiMethod method = PsiTreeUtil.getParentOfType(el, PsiMethod.class);
                            if (method != null) ctx.setMethodName(method.getName());
                        }

                    } else {
                        // 无法解析 PSI 文件, 仅设置选中代码和文件名
                        ctx.setSelectedCode(selected);
                        ctx.setFileName(vf.getName());
                    }
                } else {
                    // 无法找到虚拟文件, 仅设置选中代码
                    ctx.setSelectedCode(selected);
                    if (vurl != null) ctx.setFileName(vurl);
                }
            } catch (Exception e) {
                // 处理异常，继续下一个引用
                ctx.setSelectedCode(selected);
            }

            contexts.add(ctx);
        }

        return contexts;
    }
}
