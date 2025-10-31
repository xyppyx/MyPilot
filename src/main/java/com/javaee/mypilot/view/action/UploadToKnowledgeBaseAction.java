package com.javaee.mypilot.view.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.javaee.mypilot.service.RagService;
import com.javaee.mypilot.service.ConfigService;
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
        descriptor.setDescription("支持 PDF, PPT, PPTX, DOC, DOCX, TXT, MD 格式。可选择多个文件或文件夹。");

        // 设置文件过滤器（可选）
        descriptor.withFileFilter(file -> {
            if (file.isDirectory()) {
                return true;
            }
            String extension = file.getExtension();
            return extension != null &&
                   (extension.equalsIgnoreCase("pdf") ||
                    extension.equalsIgnoreCase("ppt") ||
                    extension.equalsIgnoreCase("pptx") ||
                    extension.equalsIgnoreCase("doc") ||
                    extension.equalsIgnoreCase("docx") ||
                    extension.equalsIgnoreCase("txt") ||
                    extension.equalsIgnoreCase("md"));
        });

        // 打开文件选择对话框
        VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, project, null);

        if (selectedFiles.length == 0) {
            return; // 用户取消选择
        }

        // 转换为 File 对象并收集文件信息用于显示
        List<File> filesToUpload = new ArrayList<>();
        List<File> foldersToUpload = new ArrayList<>();
        StringBuilder selectedItemsInfo = new StringBuilder();

        for (VirtualFile vf : selectedFiles) {
            File file = new File(vf.getPath());
            if (file.isDirectory()) {
                foldersToUpload.add(file);
                selectedItemsInfo.append("📁 ").append(file.getName()).append("\n");
            } else {
                filesToUpload.add(file);
                selectedItemsInfo.append("📄 ").append(file.getName()).append("\n");
            }
        }

        // 获取服务
        RagService ragService = RagService.getInstance(project);
        ConfigService configService = ConfigService.getInstance(project);

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
                        resultMessage.append("上传结果：\n\n");
                        
                        // 显示已选择的文件和文件夹
                        resultMessage.append("已选择的文件和文件夹：\n");
                        resultMessage.append(selectedItemsInfo.toString()).append("\n");

                        // 上传文件
                        if (!filesToUpload.isEmpty()) {
                            indicator.setText("正在上传文件...");
                            indicator.setFraction((double) processed / totalFiles);

                            boolean success = ragService.uploadFilesToKnowledgeBase(filesToUpload);
                            if (success) {
                                resultMessage.append("✅ 成功上传 ")
                                        .append(filesToUpload.size())
                                        .append(" 个文件\n");
                                
                                // 显示上传的文件列表
                                resultMessage.append("\n   已上传的文件列表：\n");
                                for (int i = 0; i < filesToUpload.size(); i++) {
                                    File file = filesToUpload.get(i);
                                    resultMessage.append("     ")
                                            .append(String.format("%d. ", i + 1))
                                            .append(file.getName());
                                    // 显示文件大小（如果可用）
                                    if (file.exists() && file.isFile()) {
                                        long sizeKB = file.length() / 1024;
                                        resultMessage.append(" (").append(sizeKB).append(" KB)");
                                    }
                                    resultMessage.append("\n");
                                }
                            } else {
                                resultMessage.append("❌ 文件上传失败\n");
                                allSuccess = false;
                            }
                            processed += filesToUpload.size();
                        }

                        // 上传文件夹
                        for (File folder : foldersToUpload) {
                            if (indicator.isCanceled()) {
                                resultMessage.append("\n⚠️ 上传已取消\n");
                                allSuccess = false;
                                break;
                            }

                            indicator.setText("正在处理文件夹: " + folder.getName());
                            indicator.setFraction((double) processed / totalFiles);

                            // 在上传前收集文件夹中的支持文件列表
                            List<File> folderFiles = new ArrayList<>();
                            collectSupportedFiles(folder, folderFiles);

                            boolean success = ragService.uploadFolderToKnowledgeBase(folder);
                            if (success) {
                                resultMessage.append("✅ 成功处理文件夹: ")
                                        .append(folder.getName())
                                        .append(" (包含 ")
                                        .append(folderFiles.size())
                                        .append(" 个文件)\n");
                                
                                // 显示文件夹内上传的文件列表
                                if (!folderFiles.isEmpty()) {
                                    resultMessage.append("\n   文件夹内已上传的文件：\n");
                                    for (File file : folderFiles) {
                                        // 显示相对路径，更清晰
                                        String relativePath = getRelativePath(folder, file);
                                        resultMessage.append("     • ").append(relativePath).append("\n");
                                    }
                                }
                            } else {
                                resultMessage.append("❌ 文件夹处理失败: ")
                                        .append(folder.getName())
                                        .append("\n");
                                allSuccess = false;
                            }
                            processed++;
                        }

                        // 显示结果
                        indicator.setFraction(1.0);
                        indicator.setText("上传完成");

                        // 获取实际上传使用的路径并更新配置
                        String actualUploadPath = configService.getUserUploadPath();
                        if (actualUploadPath == null || actualUploadPath.isEmpty()) {
                            actualUploadPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "userUploads";
                        }
                        
                        // 确保配置中保存了正确的路径
                        ConfigService.Config config = configService.getState();
                        if (config != null && 
                            (config.userUploadPath == null || 
                             config.userUploadPath.isEmpty() || 
                             !config.userUploadPath.equals(actualUploadPath))) {
                            configService.setUserUploadPath(actualUploadPath);
                        }
                        
                        resultMessage.append("\n📁 保存路径: ").append(actualUploadPath).append("\n");

                        // 获取知识库统计信息
                        String stats = ragService.getKnowledgeBaseStats();
                        resultMessage.append("\n").append(stats);

                        // 在UI线程显示结果对话框
                        boolean finalAllSuccess = allSuccess;
                        String finalMessage = resultMessage.toString();
                        com.intellij.openapi.application.ApplicationManager.getApplication()
                                .invokeLater(() -> {
                                    if (finalAllSuccess) {
                                        Messages.showInfoMessage(
                                                project,
                                                finalMessage,
                                                "上传成功"
                                        );
                                    } else {
                                        Messages.showWarningDialog(
                                                project,
                                                finalMessage,
                                                "上传完成（部分失败）"
                                        );
                                    }
                                });
                    }
                }
        );
    }

    /**
     * 递归收集文件夹中所有支持的文件
     */
    private void collectSupportedFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectSupportedFiles(file, result);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".pdf") || fileName.endsWith(".ppt") || fileName.endsWith(".pptx") ||
                    fileName.endsWith(".doc") || fileName.endsWith(".docx") ||
                    fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                    result.add(file);
                }
            }
        }
    }

    /**
     * 获取文件相对于文件夹的相对路径
     */
    private String getRelativePath(File baseDir, File file) {
        try {
            String basePath = baseDir.getAbsolutePath();
            String filePath = file.getAbsolutePath();
            if (filePath.startsWith(basePath)) {
                String relative = filePath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative;
            }
            return file.getName();
        } catch (Exception e) {
            return file.getName();
        }
    }
}

