package com.javaee.mypilot.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.javaee.mypilot.service.RagService;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户上传文档到知识库的Action
 * 用户可以选择单个/多个文件或文件夹，上传到RAG知识库
 */
public class UploadToKnowledgeBaseAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 创建文件选择器（支持选择文件和文件夹）
        FileChooserDescriptor descriptor = new FileChooserDescriptor(
                true,  // 允许选择文件
                true,  // 允许选择文件夹
                false, // 不允许选择JAR
                false, // 不允许选择JAR内容
                false, // 不允许选择JAR内容
                true   // 允许多选
        );

        descriptor.setTitle("选择文档上传到知识库");
        descriptor.setDescription("支持 PDF, PPT, PPTX 格式。可选择多个文件或文件夹。");

        // 设置文件过滤器（可选）
        descriptor.withFileFilter(file -> {
            if (file.isDirectory()) {
                return true;
            }
            String extension = file.getExtension();
            return extension != null &&
                   (extension.equalsIgnoreCase("pdf") ||
                    extension.equalsIgnoreCase("ppt") ||
                    extension.equalsIgnoreCase("pptx"));
        });

        // 打开文件选择对话框
        VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, project, null);

        if (selectedFiles.length == 0) {
            return; // 用户取消选择
        }

        // 转换为 File 对象
        List<File> filesToUpload = new ArrayList<>();
        List<File> foldersToUpload = new ArrayList<>();

        for (VirtualFile vf : selectedFiles) {
            File file = new File(vf.getPath());
            if (file.isDirectory()) {
                foldersToUpload.add(file);
            } else {
                filesToUpload.add(file);
            }
        }

        // 获取 RagService
        RagService ragService = RagService.getInstance(project);

        // 异步执行上传任务（避免阻塞UI）
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
                new com.intellij.openapi.progress.Task.Backgroundable(
                        project, "上传文档到知识库", true) {

                    @Override
                    public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                        indicator.setIndeterminate(false);

                        int totalFiles = filesToUpload.size() + foldersToUpload.size();
                        int processed = 0;

                        boolean allSuccess = true;
                        StringBuilder resultMessage = new StringBuilder();

                        // 上传文件
                        if (!filesToUpload.isEmpty()) {
                            indicator.setText("正在上传文件...");
                            indicator.setFraction((double) processed / totalFiles);

                            boolean success = ragService.uploadFilesToKnowledgeBase(filesToUpload);
                            if (success) {
                                resultMessage.append("成功上传 ")
                                        .append(filesToUpload.size())
                                        .append(" 个文件\n");
                            } else {
                                resultMessage.append("文件上传失败\n");
                                allSuccess = false;
                            }
                            processed += filesToUpload.size();
                        }

                        // 上传文件夹
                        for (File folder : foldersToUpload) {
                            if (indicator.isCanceled()) {
                                break;
                            }

                            indicator.setText("正在处理文件夹: " + folder.getName());
                            indicator.setFraction((double) processed / totalFiles);

                            boolean success = ragService.uploadFolderToKnowledgeBase(folder);
                            if (success) {
                                resultMessage.append("成功处理文件夹: ")
                                        .append(folder.getName())
                                        .append("\n");
                            } else {
                                resultMessage.append("文件夹处理失败: ")
                                        .append(folder.getName())
                                        .append("\n");
                                allSuccess = false;
                            }
                            processed++;
                        }

                        // 显示结果
                        indicator.setFraction(1.0);
                        indicator.setText("上传完成");

                        // 获取知识库统计信息
                        String stats = ragService.getKnowledgeBaseStats();
                        resultMessage.append("\n").append(stats);

                        // 在UI线程显示结果对话框
                        boolean finalAllSuccess = allSuccess;
                        com.intellij.openapi.application.ApplicationManager.getApplication()
                                .invokeLater(() -> {
                                    if (finalAllSuccess) {
                                        Messages.showInfoMessage(
                                                project,
                                                resultMessage.toString(),
                                                "上传成功"
                                        );
                                    } else {
                                        Messages.showWarningDialog(
                                                project,
                                                resultMessage.toString(),
                                                "上传完成（部分失败）"
                                        );
                                    }
                                });
                    }
                }
        );
    }
}

