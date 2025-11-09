package com.javaee.mypilot.view.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.javaee.mypilot.core.enums.CodeOpt;
import com.javaee.mypilot.core.model.agent.CodeAction;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码更改审查对话框
 * 显示所有代码更改，每个更改都有复选框，用户可以勾选想要应用的更改
 * 类似 Cursor 的体验
 */
public class CodeChangesReviewDialog extends DialogWrapper {
    
    private final Project project;
    private final List<CodeAction> codeActions;
    private final List<JCheckBox> checkBoxes;
    private JPanel changesPanel;
    
    public CodeChangesReviewDialog(Project project, List<CodeAction> codeActions) {
        super(project, true);
        this.project = project;
        this.codeActions = new ArrayList<>(codeActions);
        this.checkBoxes = new ArrayList<>();
        
        setTitle("审查代码更改建议");
        setOKButtonText("应用选中的更改");
        setCancelButtonText("取消");
        init();
        
        // 默认选中所有更改
        checkBoxes.forEach(cb -> cb.setSelected(true));
    }
    
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(800, 500));
        panel.setBorder(JBUI.Borders.empty(10));
        
        // 顶部说明
        JBLabel infoLabel = new JBLabel(
            "<html>" +
            "请审查以下代码更改建议。勾选您想要应用的更改，然后点击「应用选中的更改」。<br>" +
            "您可以取消勾选不想应用的更改。" +
            "</html>"
        );
        infoLabel.setBorder(JBUI.Borders.empty(0, 0, 10, 0));
        panel.add(infoLabel, BorderLayout.NORTH);
        
        // 更改列表面板
        changesPanel = new JPanel();
        changesPanel.setLayout(new BoxLayout(changesPanel, BoxLayout.Y_AXIS));
        changesPanel.setBorder(JBUI.Borders.empty(5));
        
        // 为每个代码更改创建一个带复选框的面板
        for (int i = 0; i < codeActions.size(); i++) {
            CodeAction action = codeActions.get(i);
            JPanel actionPanel = createActionPanel(action, i + 1);
            changesPanel.add(actionPanel);
            changesPanel.add(Box.createVerticalStrut(10)); // 间距
        }
        
        JBScrollPane scrollPane = new JBScrollPane(changesPanel);
        scrollPane.setBorder(JBUI.Borders.compound(
            JBUI.Borders.customLine(new Color(200, 200, 200), 1, 0, 0, 0),
            JBUI.Borders.empty(5)
        ));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 底部操作按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllButton = new JButton("全选");
        JButton deselectAllButton = new JButton("全不选");
        selectAllButton.addActionListener(e -> {
            checkBoxes.forEach(cb -> cb.setSelected(true));
            updateApplyButtonState();
        });
        deselectAllButton.addActionListener(e -> {
            checkBoxes.forEach(cb -> cb.setSelected(false));
            updateApplyButtonState();
        });
        bottomPanel.add(selectAllButton);
        bottomPanel.add(deselectAllButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 为单个代码更改创建面板
     */
    private JPanel createActionPanel(CodeAction action, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.compound(
            JBUI.Borders.customLine(new Color(220, 220, 220), 1),
            JBUI.Borders.empty(10)
        ));
        panel.setBackground(Color.WHITE);
        
        // 左侧：复选框
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(true);
        checkBox.addActionListener(e -> updateApplyButtonState());
        checkBoxes.add(checkBox);
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(false);
        leftPanel.add(checkBox, BorderLayout.NORTH);
        panel.add(leftPanel, BorderLayout.WEST);
        
        // 中间：更改信息
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);
        
        // 更改编号和类型
        String typeText = getTypeText(action.getOpt());
        JBLabel titleLabel = new JBLabel(
            String.format("<html><b>更改 #%d - %s</b></html>", index, typeText)
        );
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        infoPanel.add(titleLabel);
        infoPanel.add(Box.createVerticalStrut(5));
        
        // 文件路径
        JBLabel fileLabel = new JBLabel(
            String.format("<html>文件: <code>%s</code></html>", action.getFilePath())
        );
        fileLabel.setForeground(new Color(100, 100, 100));
        infoPanel.add(fileLabel);
        
        // 行号范围
        if (action.getStartLine() > 0 && action.getEndLine() >= action.getStartLine()) {
            JBLabel lineLabel = new JBLabel(
                String.format("行号: %d - %d", action.getStartLine(), action.getEndLine())
            );
            lineLabel.setForeground(new Color(100, 100, 100));
            infoPanel.add(lineLabel);
        }
        
        // 预览代码（如果较短）
        if (action.getNewCode() != null && !action.getNewCode().isEmpty()) {
            String preview = action.getNewCode();
            if (preview.length() > 150) {
                preview = preview.substring(0, 150) + "...";
            }
            preview = preview.replace("\n", " ").replace("\r", "");
            JBLabel codePreviewLabel = new JBLabel(
                String.format("<html><code style='color: #0066CC;'>%s</code></html>", 
                    htmlEscape(preview))
            );
            codePreviewLabel.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
            infoPanel.add(codePreviewLabel);
        }
        
        panel.add(infoPanel, BorderLayout.CENTER);
        
        // 右侧：查看diff按钮
        JButton viewDiffButton = new JButton("查看diff");
        viewDiffButton.addActionListener(e -> {
            // 打开对应的diff窗口
            com.javaee.mypilot.infra.agent.DiffManager diffManager = 
                project.getService(com.javaee.mypilot.infra.agent.DiffManager.class);
            if (diffManager != null) {
                // 调用公共方法显示diff
                diffManager.showDiffForAction(action);
            }
        });
        panel.add(viewDiffButton, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * 获取操作类型的文本描述
     */
    private String getTypeText(CodeOpt opt) {
        if (opt == null) return "未知操作";
        switch (opt) {
            case REPLACE:
                return "替换代码";
            case INSERT:
                return "插入代码";
            case DELETE:
                return "删除代码";
            default:
                return "未知操作";
        }
    }
    
    /**
     * HTML转义
     */
    private String htmlEscape(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * 更新应用按钮的状态
     */
    private void updateApplyButtonState() {
        boolean hasSelected = checkBoxes.stream().anyMatch(JCheckBox::isSelected);
        setOKActionEnabled(hasSelected);
    }
    
    /**
     * 获取选中的代码更改列表
     */
    public List<CodeAction> getSelectedCodeActions() {
        List<CodeAction> selected = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) {
                selected.add(codeActions.get(i));
            }
        }
        return selected;
    }
    
    @Override
    protected void doOKAction() {
        List<CodeAction> selected = getSelectedCodeActions();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(
                getContentPanel(),
                "请至少选择一个要应用的更改。",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        super.doOKAction();

        // 对话框关闭时，清理仍为空的临时新文件
        com.javaee.mypilot.infra.agent.DiffManager diffManager =
            project.getService(com.javaee.mypilot.infra.agent.DiffManager.class);
        if (diffManager != null) {
            diffManager.cleanupEmptyTempNewFiles();
        }
    }

    @Override
    public void doCancelAction() {
        super.doCancelAction();
        // 取消关闭也清理仍为空的临时新文件
        com.javaee.mypilot.infra.agent.DiffManager diffManager =
            project.getService(com.javaee.mypilot.infra.agent.DiffManager.class);
        if (diffManager != null) {
            diffManager.cleanupEmptyTempNewFiles();
        }
    }
}

