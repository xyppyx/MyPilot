package com.javaee.mypilot.view.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.javaee.mypilot.core.enums.ChatOpt;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.CodeReference;
import com.javaee.mypilot.service.ManageService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MyPilot 聊天面板
 * Chat panel for MyPilot interaction
 * 
 * 集成 ManageService 进行数据交互
 * 新布局：左侧历史对话列表，顶部设置区，底部模式选择
 */
public class ChatPanel extends JPanel implements PropertyChangeListener {
    
    @SuppressWarnings("unused")
    private final Project project;
    private final ManageService manageService;
    
    // UI 组件
    private JTextArea chatHistoryArea;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton clearButton;
    private JButton newSessionButton;
    private JButton historyButton;  // 历史会话按钮
    private JButton settingsButton;
    private JComboBox<ChatOpt> modeComboBox;
    private JLabel statusLabel;
    private JPopupMenu historyPopupMenu;  // 历史会话弹出菜单
    
    // 代码引用相关
    private JPanel codeReferencePanel;
    private JPanel codeEditorsContainer;  // 存放多个编辑器的容器
    private List<Editor> codeEditors;  // 存储编辑器实例以便释放
    
    public ChatPanel(Project project) {
        this.project = project;
        
        // 获取 ManageService 实例
        this.manageService = ManageService.getInstance(project);
        
        // 注册为监听器，接收 Service 的数据
        this.manageService.addPropertyChangeListener(this);
        
        initUI();
        showWelcomeMessage();
    }
    
    /**
     * 显示欢迎消息（根据UI设计文档）
     */
    private void showWelcomeMessage() {
        appendToChatHistory("欢迎使用 MyPilot - AI Coding Assistant!\n\n");
        appendToChatHistory("功能说明:\n");
        appendToChatHistory("• 在输入框输入问题，按 Ctrl+Enter 或点击发送\n");
        appendToChatHistory("• 在底部选择 ASK 模式进行 RAG 问答\n");
        appendToChatHistory("• 在底部选择 AGENT 模式进行代码辅助\n\n");
    }
    
    /**
     * 初始化 UI
     */
    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        setBorder(JBUI.Borders.empty(5));
        
        // 顶部：状态、历史会话和控制按钮
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // 中间：聊天区域（单栏）
        JPanel chatArea = createChatArea();
        add(chatArea, BorderLayout.CENTER);
        
        // 底部：代码引用 + 模式选择 + 输入区域
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 创建顶部面板：按钮组
     * 
     * 布局：[历史会话] [新会话] [清空] [设置]
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(JBUI.Borders.empty(5, 5, 10, 5));
        
        // 初始化状态标签（不显示在界面上，仅用于内部状态管理）
        statusLabel = new JLabel();
        
        // 控制按钮（右对齐）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        
        // 历史会话按钮
        historyButton = new JButton("历史会话");
        historyButton.setPreferredSize(new Dimension(90, 28));
        historyButton.addActionListener(e -> showHistoryPopup());
        buttonPanel.add(historyButton);
        
        // 新会话按钮
        newSessionButton = new JButton("新会话");
        newSessionButton.setPreferredSize(new Dimension(80, 28));
        newSessionButton.addActionListener(e -> startNewSession());
        buttonPanel.add(newSessionButton);
        
        // 清空按钮
        clearButton = new JButton("清空");
        clearButton.setPreferredSize(new Dimension(70, 28));
        clearButton.addActionListener(e -> clearChat());
        buttonPanel.add(clearButton);
        
        // 设置按钮（使用齿轮图标）
        settingsButton = new JButton("设置");
        settingsButton.setPreferredSize(new Dimension(45, 28));
        settingsButton.addActionListener(e -> openSettings());
        buttonPanel.add(settingsButton);
        
        panel.add(buttonPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * 创建聊天区域（单栏布局）
     */
    private JPanel createChatArea() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 聊天历史区域
        chatHistoryArea = new JTextArea();
        chatHistoryArea.setEditable(false);
        chatHistoryArea.setLineWrap(true);
        chatHistoryArea.setWrapStyleWord(true);
        chatHistoryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        JBScrollPane scrollPane = new JBScrollPane(chatHistoryArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建底部面板：代码引用区 + 输入区域 + 模式选择
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(JBUI.Borders.emptyTop(5));
        
        // 顶部：代码引用区域（可折叠）
        codeReferencePanel = createCodeReferencePanel();
        panel.add(codeReferencePanel, BorderLayout.NORTH);
        
        // 中间：输入控制区（垂直布局）
        JPanel inputControlPanel = new JPanel(new BorderLayout(5, 5));
        
        // 上方：输入区域 + 发送按钮
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        
        inputArea = new JBTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        inputArea.setToolTipText("在这里输入你的问题...");
        
        // 支持 Ctrl+Enter 发送
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    sendMessage();
                    e.consume();
                }
            }
        });
        
        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        
        // 右侧：发送按钮
        JPanel sendPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
        sendButton = new JButton("发送");
        sendButton.setToolTipText("发送消息 (Ctrl+Enter)");
        sendButton.setPreferredSize(new Dimension(80, 60));
        sendButton.addActionListener(e -> sendMessage());
        sendPanel.add(sendButton);
        
        inputPanel.add(sendPanel, BorderLayout.EAST);
        
        inputControlPanel.add(inputPanel, BorderLayout.NORTH);
        
        // 下方：模式选择
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JLabel modeLabel = new JLabel("模式:");
        modePanel.add(modeLabel);
        
        modeComboBox = new JComboBox<>(ChatOpt.values());
        modeComboBox.setSelectedItem(manageService.getCurrentOpt());
        modeComboBox.setToolTipText("选择对话模式：ASK (RAG问答) 或 AGENT (代码助手)");
        modeComboBox.setPreferredSize(new Dimension(100, 25));
        modeComboBox.addActionListener(e -> {
            ChatOpt selectedOpt = (ChatOpt) modeComboBox.getSelectedItem();
            if (selectedOpt != null) {
                manageService.setCurrentOpt(selectedOpt);
            }
        });
        modePanel.add(modeComboBox);
        
        inputControlPanel.add(modePanel, BorderLayout.SOUTH);
        
        panel.add(inputControlPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建代码引用显示面板（使用真实编辑器）
     */
    private JPanel createCodeReferencePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(JBUI.Borders.empty(5));
        panel.setVisible(false); // 默认隐藏，有引用时才显示
        
        // 初始化编辑器列表
        codeEditors = new ArrayList<>();
        
        // 创建容器来存放多个编辑器
        codeEditorsContainer = new JPanel();
        codeEditorsContainer.setLayout(new BoxLayout(codeEditorsContainer, BoxLayout.Y_AXIS));
        
        JBScrollPane scrollPane = new JBScrollPane(codeEditorsContainer);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 发送消息
     */
    private void sendMessage() {
        String question = inputArea.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        
        // 清空输入框
        inputArea.setText("");
        
        // 禁用发送按钮
        sendButton.setEnabled(false);
        
        // 显示用户消息（包含代码引用）
        displayUserMessageWithReferences(question);
        
        // 获取当前的代码上下文
        CodeContext codeContext = new CodeContext();
        
        // 获取当前的聊天选项
        ChatOpt chatOpt = manageService.getCurrentOpt();
        
        // 在后台线程调用 ManageService
        new Thread(() -> {
            try {
                manageService.handleRequest(question, chatOpt, codeContext);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendToChatHistory("\n发生错误: " + ex.getMessage() + "\n\n");
                    sendButton.setEnabled(true);
                });
            }
        }, "MyPilot-Request-Thread").start();
    }
    
    /**
     * 显示用户消息（包含代码引用，编辑器风格）
     */
    private void displayUserMessageWithReferences(String question) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("\n👤 You:\n");
        
        // 获取当前的代码引用
        List<CodeReference> references = manageService.getCodeReferences();
        
        // 如果有代码引用，以编辑器风格显示
        if (!references.isEmpty()) {
            for (int i = 0; i < references.size(); i++) {
                CodeReference ref = references.get(i);
                String fileName = extractFileName(ref.getVirtualFileUrl());
                
                // 文件头部（类似编辑器的标签）
                messageBuilder.append("\n╭─────────────────────────────────────────────╮\n");
                messageBuilder.append(String.format("│ 📄 %s:%d-%d", 
                    fileName, ref.getStartLine(), ref.getEndLine()));
                
                // 填充空格对齐
                int padding = 44 - fileName.length() - String.valueOf(ref.getStartLine()).length() 
                             - String.valueOf(ref.getEndLine()).length() - 7;
                if (padding > 0) {
                    messageBuilder.append(" ".repeat(padding));
                }
                messageBuilder.append("│\n");
                messageBuilder.append("├─────────────────────────────────────────────┤\n");
                
                // 显示代码内容（带行号，类似编辑器）
                String[] codeLines = ref.getSelectedCode().split("\n");
                int lineNum = ref.getStartLine();
                
                for (String line : codeLines) {
                    // 行号（右对齐，4位宽度）
                    String lineNumStr = String.format("%4d", lineNum);
                    
                    // 限制行长度，避免显示过长
                    String displayLine = line;
                    if (displayLine.length() > 80) {
                        displayLine = displayLine.substring(0, 77) + "...";
                    }
                    
                    messageBuilder.append(String.format("│ %s │ %s\n", lineNumStr, displayLine));
                    lineNum++;
                }
                
                messageBuilder.append("╰─────────────────────────────────────────────╯\n");
                
                // 多个引用之间添加间隔
                if (i < references.size() - 1) {
                    messageBuilder.append("\n");
                }
            }
            messageBuilder.append("\n");
        }
        
        // 添加用户问题
        messageBuilder.append(question).append("\n");
        
        appendToChatHistory(messageBuilder.toString());
    }
    
    /**
     * 添加内容到聊天历史
     */
    private void appendToChatHistory(String text) {
        SwingUtilities.invokeLater(() -> {
            chatHistoryArea.append(text);
            chatHistoryArea.setCaretPosition(chatHistoryArea.getDocument().getLength());
        });
    }
    
    /**
     * 清空当前显示（仅清空UI显示，不删除历史对话）
     */
    private void clearChatDisplay() {
        chatHistoryArea.setText("");
        showWelcomeMessage();
    }
    
    /**
     * 清空聊天历史（删除所有历史对话）
     */
    private void clearChat() {
        // 确认删除所有历史对话
        List<String> sessions = manageService.getAllChatTitles();
        if (sessions == null || sessions.isEmpty()) {
            // 如果没有历史对话，只清空当前显示
            clearChatDisplay();
            return;
        }
        
        // 弹出确认对话框
        int result = JOptionPane.showConfirmDialog(
            this,
            "确定要删除所有历史对话吗？\n共有 " + sessions.size() + " 个会话将被删除。\n此操作无法撤销。",
            "确认删除所有历史对话",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            int deletedCount = manageService.deleteAllChatSessions();
            if (deletedCount > 0) {
                // 清空当前显示
                clearChatDisplay();
                
                // 显示删除成功提示
                JOptionPane.showMessageDialog(
                    this,
                    "已成功删除 " + deletedCount + " 个历史对话",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        }
    }
    
    /**
     * 开始新会话
     */
    private void startNewSession() {
        manageService.startNewSession();
        clearChatDisplay(); // 只清空显示，不删除历史对话
    }
    
    /**
     * 打开设置对话框（跳转到 Settings → Tools → MyPilot）
     */
    private void openSettings() {
        // 使用 IntelliJ IDEA API 打开设置对话框并导航到 MyPilot 配置页面
        SwingUtilities.invokeLater(() -> {
            try {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, "MyPilot");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, 
                    "无法打开设置页面: " + ex.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    /**
     * 显示历史会话弹出菜单
     */
    private void showHistoryPopup() {
        // 创建弹出菜单
        historyPopupMenu = new JPopupMenu();
        
        // 从 ManageService 获取所有会话
        List<String> sessions = manageService.getAllChatTitles();
        
        // 添加"当前会话"选项
        JMenuItem currentSessionItem = new JMenuItem("当前会话");
        currentSessionItem.setEnabled(false); // 默认禁用，因为已经在当前会话
        historyPopupMenu.add(currentSessionItem);
        
        // 添加分隔线
        if (!sessions.isEmpty()) {
            historyPopupMenu.addSeparator();
        }
        
        // 添加历史会话选项
        if (sessions.isEmpty()) {
            JMenuItem noHistoryItem = new JMenuItem("(暂无历史会话)");
            noHistoryItem.setEnabled(false);
            historyPopupMenu.add(noHistoryItem);
        } else {
            for (String session : sessions) {
                if (session != null && !session.trim().isEmpty()) {
                    // 创建自定义菜单项，包含会话名称和删除按钮
                    JPanel sessionPanel = new JPanel(new BorderLayout(5, 0));
                    sessionPanel.setOpaque(false);
                    
                    // 会话名称标签（可点击）
                    JLabel sessionLabel = new JLabel(session);
                    sessionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    sessionLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            historyPopupMenu.setVisible(false);
                            switchToSession(session);
                        }
                    });
                    sessionPanel.add(sessionLabel, BorderLayout.CENTER);
                    
                    // 删除按钮（×）
                    JLabel deleteLabel = new JLabel("×");
                    deleteLabel.setFont(deleteLabel.getFont().deriveFont(Font.BOLD, 14f));
                    deleteLabel.setForeground(new Color(150, 150, 150));
                    deleteLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    deleteLabel.setToolTipText("删除此会话");
                    deleteLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            e.consume(); // 阻止事件传播
                            deleteSession(session);
                        }
                        
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            deleteLabel.setForeground(new Color(220, 50, 50));
                        }
                        
                        @Override
                        public void mouseExited(MouseEvent e) {
                            deleteLabel.setForeground(new Color(150, 150, 150));
                        }
                    });
                    sessionPanel.add(deleteLabel, BorderLayout.EAST);
                    
                    // 将自定义面板包装为菜单项
                    JMenuItem sessionItem = new JMenuItem();
                    sessionItem.setLayout(new BorderLayout());
                    sessionItem.add(sessionPanel, BorderLayout.CENTER);
                    historyPopupMenu.add(sessionItem);
                }
            }
        }
        
        // 在历史会话按钮下方显示弹出菜单
        historyPopupMenu.show(historyButton, 0, historyButton.getHeight());
    }
    
    /**
     * 删除指定会话
     */
    private void deleteSession(String sessionName) {
        // 确认删除对话框
        int result = JOptionPane.showConfirmDialog(
            this,
            "确定要删除会话 \"" + sessionName + "\" 吗？\n此操作无法撤销。",
            "确认删除",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            boolean deleted = manageService.deleteChatSessionByTitle(sessionName);
            if (deleted) {
                // 关闭弹出菜单
                historyPopupMenu.setVisible(false);
                
                // 检查当前显示的内容是否是被删除的会话
                // 如果是，清空显示并显示欢迎消息
                String currentText = chatHistoryArea.getText();
                if (currentText.contains("已切换到会话: " + sessionName)) {
                    chatHistoryArea.setText("");
                    showWelcomeMessage();
                }
                
                // 显示删除成功提示
                JOptionPane.showMessageDialog(
                    this,
                    "会话已删除",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                // 显示删除失败提示
                JOptionPane.showMessageDialog(
                    this,
                    "删除会话失败",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    /**
     * 切换到指定会话
     */
    private void switchToSession(String sessionName) {
        SwingUtilities.invokeLater(() -> {
            // 清空当前聊天历史显示
            chatHistoryArea.setText("");
            
            // 从 ManageService 加载对应会话的聊天记录
            List<ChatMessage> historyMessages = manageService.switchToSessionByTitle(sessionName);
            
            if (historyMessages == null || historyMessages.isEmpty()) {
                appendToChatHistory("已切换到会话: " + sessionName + "\n");
                appendToChatHistory("（该会话暂无聊天记录）\n\n");
                showWelcomeMessage();
                return;
            }
            
            // 显示会话标题
            appendToChatHistory("已切换到会话: " + sessionName + "\n\n");
            appendToChatHistory("────────────────────────────────────\n\n");
            
            // 按时间戳排序消息（确保按时间顺序显示）
            List<ChatMessage> sortedMessages = historyMessages.stream()
                    .sorted(Comparator.comparing(
                            ChatMessage::getTimestamp,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .collect(Collectors.toList());
            
            // 显示所有历史消息
            for (ChatMessage message : sortedMessages) {
                if (message.isUserMessage()) {
                    // 显示用户消息
                    appendToChatHistory("👤 You:\n");
                    appendToChatHistory(message.getContent() + "\n\n");
                } else {
                    // 显示助手消息（应用 markdown 清理）
                    String content = cleanMarkdown(message.getContent());
                    appendToChatHistory("🤖 MyPilot: " + content + "\n\n");
                }
            }
            
            appendToChatHistory("────────────────────────────────────\n\n");
        });
    }
    
    /**
     * 加载历史对话列表（预加载，优化点击历史会话按钮的响应速度）
     */
    private void loadHistorySessions() {
        // 历史会话现在通过弹出菜单显示，点击"历史会话"按钮时动态加载
        // 这个方法可以用于预加载或刷新历史会话数据
    }
    
    /**
     * PropertyChangeListener 实现
     * 接收来自 ManageService 的数据
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        
        switch (propertyName) {
            case "userMessage":
                // 显示用户消息
                ChatMessage userMsg = (ChatMessage) evt.getNewValue();
                displayUserMessage(userMsg);
                break;
                
            case "assistantMessage":
                // 显示助手回复
                ChatMessage assistantMsg = (ChatMessage) evt.getNewValue();
                displayAssistantMessage(assistantMsg);
                break;
                
            case "status":
                // 更新状态显示
                String status = (String) evt.getNewValue();
                updateStatus(status);
                break;
                
            case "error":
                // 显示错误信息
                String errorMsg = (String) evt.getNewValue();
                showError(errorMsg);
                break;
                
            case "codeReferencesUpdated":
                // 更新代码引用显示
                @SuppressWarnings("unchecked")
                List<CodeReference> refs = (List<CodeReference>) evt.getNewValue();
                updateCodeReferences(refs);
                break;
        }
    }
    
    /**
     * 更新代码引用显示（使用真实编辑器）
     */
    private void updateCodeReferences(List<CodeReference> codeReferences) {
        SwingUtilities.invokeLater(() -> {
            // 释放旧的编辑器
            for (Editor editor : codeEditors) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
            codeEditors.clear();
            codeEditorsContainer.removeAll();
            
            if (codeReferences == null || codeReferences.isEmpty()) {
                // 没有引用时隐藏面板
                codeReferencePanel.setVisible(false);
                return;
            }
            
            // 显示面板并添加引用
            codeReferencePanel.setVisible(true);
            
            for (int i = 0; i < codeReferences.size(); i++) {
                CodeReference ref = codeReferences.get(i);
                final int index = i;  // 用于删除操作
                String fileName = extractFileName(ref.getVirtualFileUrl());
                
                // 创建标题面板 - 优化设计
                JPanel titlePanel = new JPanel(new BorderLayout());
                titlePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                titlePanel.setBorder(JBUI.Borders.compound(
                    JBUI.Borders.customLine(new Color(200, 200, 200), 1, 1, 0, 1),
                    JBUI.Borders.empty(6, 10)
                ));
                titlePanel.setBackground(new Color(250, 250, 250));
                
                // 文件名标签
                JLabel fileNameLabel = new JLabel(fileName);
                fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 12f));
                fileNameLabel.setForeground(new Color(60, 60, 60));
                
                // 行号标签（灰色）
                JLabel lineRangeLabel = new JLabel(String.format("  (%d-%d)", 
                    ref.getStartLine(), ref.getEndLine()));
                lineRangeLabel.setFont(lineRangeLabel.getFont().deriveFont(Font.PLAIN, 11f));
                lineRangeLabel.setForeground(new Color(120, 120, 120));
                
                // 组合标题
                JPanel titleContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                titleContent.setOpaque(false);
                titleContent.add(fileNameLabel);
                titleContent.add(lineRangeLabel);
                
                titlePanel.add(titleContent, BorderLayout.WEST);
                
                // 添加删除按钮
                JButton deleteButton = new JButton("×");
                deleteButton.setFont(deleteButton.getFont().deriveFont(Font.BOLD, 16f));
                deleteButton.setForeground(new Color(150, 150, 150));
                deleteButton.setPreferredSize(new Dimension(30, 24));
                deleteButton.setContentAreaFilled(false);
                deleteButton.setBorderPainted(false);
                deleteButton.setFocusPainted(false);
                deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                deleteButton.setToolTipText("删除此引用");
                
                // 鼠标悬停效果
                deleteButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        deleteButton.setForeground(new Color(220, 50, 50));
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        deleteButton.setForeground(new Color(150, 150, 150));
                    }
                });
                
                deleteButton.addActionListener(e -> manageService.removeCodeReference(index));
                titlePanel.add(deleteButton, BorderLayout.EAST);
                
                codeEditorsContainer.add(titlePanel);
                
                // 创建编辑器
                EditorFactory editorFactory = EditorFactory.getInstance();
                Document document = editorFactory.createDocument(ref.getSelectedCode());
                Editor editor = editorFactory.createViewer(document, project);
                
                // 根据文件扩展名设置语法高亮
                if (editor instanceof EditorEx) {
                    FileType fileType = FileTypeManager.getInstance()
                        .getFileTypeByFileName(fileName);
                    ((EditorEx) editor).setHighlighter(
                        EditorHighlighterFactory.getInstance()
                            .createEditorHighlighter(project, fileType)
                    );
                }
                
                // 配置编辑器
                EditorSettings settings = editor.getSettings();
                settings.setLineNumbersShown(true);
                settings.setLineMarkerAreaShown(false);
                settings.setFoldingOutlineShown(false);
                settings.setAdditionalColumnsCount(0);
                settings.setAdditionalLinesCount(0);
                settings.setRightMarginShown(false);
                settings.setCaretRowShown(false);
                
                if (editor instanceof EditorEx) {
                    ((EditorEx) editor).setVerticalScrollbarVisible(false);
                    ((EditorEx) editor).setHorizontalScrollbarVisible(true);
                }
                
                // 计算编辑器高度（每行约20像素，无限制）
                int lineCount = ref.getSelectedCode().split("\n").length;
                int editorHeight = lineCount * 20 + 10;
                
                // 编辑器组件包装面板（添加边框）
                JPanel editorWrapper = new JPanel(new BorderLayout());
                editorWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, editorHeight));
                editorWrapper.setBorder(JBUI.Borders.customLine(new Color(200, 200, 200), 0, 1, 1, 1));
                
                JComponent editorComponent = editor.getComponent();
                editorComponent.setPreferredSize(new Dimension(0, editorHeight));
                editorWrapper.add(editorComponent, BorderLayout.CENTER);
                
                codeEditorsContainer.add(editorWrapper);
                codeEditors.add(editor);
                
                // 添加间隔
                codeEditorsContainer.add(Box.createVerticalStrut(10));
            }
            
            codeEditorsContainer.revalidate();
            codeEditorsContainer.repaint();
        });
    }
    
    /**
     * 从文件 URL 中提取文件名
     */
    private String extractFileName(String fileUrl) {
        if (fileUrl == null) {
            return "未知文件";
        }
        int lastSlash = fileUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < fileUrl.length() - 1) {
            return fileUrl.substring(lastSlash + 1);
        }
        return fileUrl;
    }
    
    /**
     * 显示用户消息
     */
    private void displayUserMessage(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            appendToChatHistory("────────────────────────────────────\n");
            appendToChatHistory("👤 你: " + message.getContent() + "\n");
            appendToChatHistory("────────────────────────────────────\n\n");
        });
    }
    
    /**
     * 显示助手消息
     */
    private void displayAssistantMessage(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            String content = cleanMarkdown(message.getContent());
            appendToChatHistory("🤖 MyPilot: " + content + "\n\n");
            
            // 重新启用发送按钮
            sendButton.setEnabled(true);
        });
    }
    
    /**
     * 清理 markdown 符号（删除 #、* 和 - 符号）
     * 除了数字开头的标题，其它文字首行缩进4格
     * 删除所有空行
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // 删除所有 # 符号
        text = text.replace("#", "");
        // 删除所有 * 符号
        text = text.replace("*", "");
        // 删除 - （减号加空格）符号，但保留前面的缩进空格
        // 使用正则表达式匹配行首的缩进空格 + "- "，替换为只保留缩进空格
        text = text.replaceAll("(?m)^(\\s*)-\\s+", "$1");
        
        // 按行处理，为除数字标题外的其他文字添加首行缩进4格
        String[] lines = text.split("\n", -1); // -1 保留末尾空行
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            // 删除空行
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // 检查是否以数字开头（1-9，可能是标题）
            boolean isNumberTitle = trimmedLine.length() > 0 && 
                                   Character.isDigit(trimmedLine.charAt(0));
            
            if (isNumberTitle) {
                // 数字开头的标题，保持原样（可能已经有格式）
                result.append(line).append("\n");
            } else {
                // 其他文字，统一添加2格缩进（移除原有缩进）
                result.append("   ").append(trimmedLine).append("\n");
            }
        }
        
        // 移除最后一个多余的换行符
        if (result.length() > 0 && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        
        return result.toString();
    }
    
    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            switch (status) {
                case "processing":
                    statusLabel.setText("(正在处理...)");
                    statusLabel.setForeground(Color.BLUE);
                    sendButton.setEnabled(false);
                    break;
                case "completed":
                    statusLabel.setText("(就绪)");
                    statusLabel.setForeground(Color.GRAY);
                    sendButton.setEnabled(true);
                    break;
                case "error":
                    statusLabel.setText("(发生错误)");
                    statusLabel.setForeground(Color.RED);
                    sendButton.setEnabled(true);
                    break;
            }
        });
    }
    
    /**
     * 显示错误信息
     */
    private void showError(String errorMsg) {
        SwingUtilities.invokeLater(() -> {
            appendToChatHistory("\n错误: " + errorMsg + "\n\n");
        });
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        // 移除监听器，避免内存泄漏
        if (manageService != null) {
            manageService.removePropertyChangeListener(this);
        }
    }
}

