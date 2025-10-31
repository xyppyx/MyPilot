package com.javaee.mypilot.view.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.javaee.mypilot.infra.rag.vector.LuceneVectorDatabase;
import com.javaee.mypilot.service.RagService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库管理对话框
 * 用于查看、删除和添加知识库文件
 */
public class KnowledgeBaseManageDialog extends DialogWrapper {

    private final Project project;
    private final RagService ragService;
    private JBList<LuceneVectorDatabase.FileInfo> fileList;
    private DefaultListModel<LuceneVectorDatabase.FileInfo> listModel;
    private JLabel statusLabel;
    private JButton deleteButton;
    private JButton addButton;
    private JButton refreshButton;

    public KnowledgeBaseManageDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        this.ragService = RagService.getInstance(project);
        
        setTitle("知识库文件管理");
        setModal(true);
        
        init();
        refreshFileList();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(700, 500));
        panel.setBorder(JBUI.Borders.empty(10));

        // 顶部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        addButton = new JButton("添加文件");
        addButton.addActionListener(e -> addFiles());
        buttonPanel.add(addButton);
        
        deleteButton = new JButton("删除选中");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedFile());
        buttonPanel.add(deleteButton);
        
        refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> refreshFileList());
        buttonPanel.add(refreshButton);
        
        panel.add(buttonPanel, BorderLayout.NORTH);

        // 文件列表
        listModel = new DefaultListModel<>();
        fileList = new JBList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new FileInfoCellRenderer());
        
        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                deleteButton.setEnabled(fileList.getSelectedIndex() >= 0);
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(fileList);
        scrollPane.setBorder(JBUI.Borders.compound(
            JBUI.Borders.customLine(new Color(200, 200, 200), 1, 0, 0, 0),
            JBUI.Borders.empty(5)
        ));
        panel.add(scrollPane, BorderLayout.CENTER);

        // 底部状态栏
        statusLabel = new JLabel("正在加载文件列表...");
        statusLabel.setBorder(JBUI.Borders.empty(5));
        panel.add(statusLabel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 刷新文件列表
     */
    private void refreshFileList() {
        statusLabel.setText("正在加载...");
        deleteButton.setEnabled(false);
        
        // 在后台线程中加载文件列表
        SwingUtilities.invokeLater(() -> {
            try {
                List<LuceneVectorDatabase.FileInfo> files = ragService.getKnowledgeBaseFiles();
                listModel.clear();
                
                if (files.isEmpty()) {
                    statusLabel.setText("知识库为空");
                } else {
                    for (LuceneVectorDatabase.FileInfo fileInfo : files) {
                        listModel.addElement(fileInfo);
                    }
                    statusLabel.setText(String.format("共 %d 个文件，总计 %d 个文档块",
                        files.size(),
                        files.stream().mapToInt(f -> f.chunkCount).sum()));
                }
            } catch (Exception e) {
                statusLabel.setText("加载失败: " + e.getMessage());
                Messages.showErrorDialog(
                    project,
                    "加载文件列表失败: " + e.getMessage(),
                    "错误"
                );
            }
        });
    }

    /**
     * 添加文件到知识库
     */
    private void addFiles() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(
            true,  // 允许选择文件
            true,  // 允许选择文件夹
            false, false, false,
            true   // 允许多选
        );

        descriptor.setTitle("选择文档添加到知识库");
        descriptor.setDescription("支持 PDF, PPT, PPTX, DOC, DOCX, TXT, MD 格式");

        descriptor.withFileFilter(file -> {
            if (file.isDirectory()) return true;
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

        VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, project, null);
        if (selectedFiles == null || selectedFiles.length == 0) {
            return;
        }

        // 转换为 File 对象
        List<File> filesToUpload = new ArrayList<>();
        for (VirtualFile vf : selectedFiles) {
            File file = new File(vf.getPath());
            if (!file.isDirectory()) {
                filesToUpload.add(file);
            }
        }

        if (filesToUpload.isEmpty()) {
            Messages.showWarningDialog(project, "未选择任何文件", "提示");
            return;
        }

        // 在后台线程中上传文件
        statusLabel.setText("正在上传文件...");
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            new com.intellij.openapi.progress.Task.Backgroundable(
                project, "上传文件到知识库", true) {

                @Override
                public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                    indicator.setIndeterminate(false);
                    indicator.setFraction(0.0);

                    boolean success = false;
                    try {
                        for (int i = 0; i < filesToUpload.size(); i++) {
                            File file = filesToUpload.get(i);
                            indicator.setText("上传: " + file.getName());
                            indicator.setFraction((double) i / filesToUpload.size());

                            List<File> singleFile = new ArrayList<>();
                            singleFile.add(file);
                            boolean fileSuccess = ragService.uploadFilesToKnowledgeBase(singleFile);
                            if (fileSuccess) {
                                success = true;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("上传文件时发生错误: " + e.getMessage());
                        e.printStackTrace();
                    }

                    indicator.setFraction(1.0);
                    final boolean finalSuccess = success;
                    
                    // 刷新列表
                    SwingUtilities.invokeLater(() -> {
                        refreshFileList();
                        if (finalSuccess) {
                            Messages.showInfoMessage(
                                project,
                                "成功上传 " + filesToUpload.size() + " 个文件",
                                "上传完成"
                            );
                        } else {
                            Messages.showWarningDialog(
                                project,
                                "部分文件上传失败，请检查日志",
                                "上传完成"
                            );
                        }
                    });
                }
            }
        );
    }

    /**
     * 删除选中的文件
     */
    private void deleteSelectedFile() {
        LuceneVectorDatabase.FileInfo selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) {
            return;
        }

        // 静态资源不允许删除
        if (selectedFile.sourceType == com.javaee.mypilot.core.model.rag.DocumentChunk.SourceType.STATIC) {
            Messages.showWarningDialog(
                project,
                "静态资源文件不能删除",
                "提示"
            );
            return;
        }

        int result = Messages.showYesNoDialog(
            project,
            String.format("确定要删除文件 '%s' 吗？\n这将从知识库中删除所有相关的文档块（共 %d 个）。",
                selectedFile.fileName, selectedFile.chunkCount),
            "确认删除",
            Messages.getQuestionIcon()
        );

        if (result != Messages.YES) {
            return;
        }

        // 在后台线程中删除文件
        statusLabel.setText("正在删除文件...");
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            new com.intellij.openapi.progress.Task.Backgroundable(
                project, "删除文件", true) {

                @Override
                public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText("删除: " + selectedFile.fileName);

                    boolean success = ragService.deleteFileFromKnowledgeBase(selectedFile.fileName);

                    SwingUtilities.invokeLater(() -> {
                        refreshFileList();
                        if (success) {
                            Messages.showInfoMessage(
                                project,
                                "成功删除文件: " + selectedFile.fileName,
                                "删除完成"
                            );
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "删除文件失败",
                                "错误"
                            );
                        }
                    });
                }
            }
        );
    }

    /**
     * 文件信息列表单元格渲染器
     */
    private static class FileInfoCellRenderer extends JPanel implements ListCellRenderer<LuceneVectorDatabase.FileInfo> {
        private final JLabel fileNameLabel;
        private final JLabel infoLabel;

        public FileInfoCellRenderer() {
            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(5));

            fileNameLabel = new JLabel();
            fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD));
            add(fileNameLabel, BorderLayout.WEST);

            infoLabel = new JLabel();
            infoLabel.setForeground(new Color(100, 100, 100));
            add(infoLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends LuceneVectorDatabase.FileInfo> list,
            LuceneVectorDatabase.FileInfo value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            fileNameLabel.setText(value.fileName);
            
            String typeIcon = value.sourceType == com.javaee.mypilot.core.model.rag.DocumentChunk.SourceType.STATIC 
                ? "📦" : "📄";
            infoLabel.setText(String.format("%s %s | %d 个文档块",
                typeIcon, value.getSourceTypeDisplayName(), value.chunkCount));

            return this;
        }
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getCancelAction()};
    }
}

