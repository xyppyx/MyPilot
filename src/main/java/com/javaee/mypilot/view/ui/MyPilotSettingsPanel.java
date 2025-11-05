package com.javaee.mypilot.view.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.javaee.mypilot.core.enums.LlmPreset;
import com.javaee.mypilot.service.ConfigService;
import com.javaee.mypilot.service.RagService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * MyPilot 设置面板
 * 提供友好的配置界面
 */
public class MyPilotSettingsPanel {
    
    private final Project project;
    private final ConfigService configService;
    
    private JPanel mainPanel;
    private JBTabbedPane tabbedPane;
    
    // LLM 配置
    private JComboBox<LlmPreset> llmTypeComboBox;
    private JBPasswordField llmApiKeyField;
    private JBTextField llmApiUrlField;
    private JBTextField llmModelField;
    private DefaultListModel<String> profileListModel;
    private JList<String> profileList;
    private List<ConfigService.LlmProfile> profiles;
    
    // RAG 配置
    private TextFieldWithBrowseButton knowledgeBasePathField;
    private TextFieldWithBrowseButton courseMaterialPathField;
    
    // Embedding 配置
    private JComboBox<String> embeddingServiceTypeComboBox;
    private JBLabel embeddingApiKeyLabel;
    private JBPasswordField embeddingApiKeyField;
    
    // 检索配置
    private JSpinner retrievalTopKSpinner;
    private JSpinner relevanceThresholdSpinner;
    
    public MyPilotSettingsPanel(Project project) {
        this.project = project;
        this.configService = ConfigService.getInstance(project);
        this.profiles = new ArrayList<>();
        
        createUI();
        loadSettings();
    }
    
    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));
        
        tabbedPane = new JBTabbedPane();
        
        tabbedPane.addTab("LLM 配置", createLlmPanel());
        tabbedPane.addTab("RAG 配置", createRagPanel());
        tabbedPane.addTab("Embedding", createEmbeddingPanel());
        tabbedPane.addTab("检索参数", createRetrievalPanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
    }
    
    private JComponent createLlmPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(JBUI.Borders.empty(15));
        
        // 左侧：配置文件列表
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(JBUI.Borders.empty(0, 0, 0, 10));
        
        JBLabel listLabel = new JBLabel("配置档案列表:");
        listLabel.setFont(listLabel.getFont().deriveFont(Font.BOLD));
        leftPanel.add(listLabel, BorderLayout.NORTH);
        
        profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedProfile();
            }
        });
        
        JBScrollPane listScrollPane = new JBScrollPane(profileList);
        listScrollPane.setPreferredSize(new Dimension(100, 0));
        leftPanel.add(listScrollPane, BorderLayout.CENTER);
        
        // 列表操作按钮
        JPanel listButtonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        JButton addButton = new JButton("新增");
        JButton deleteButton = new JButton("删除");
        JButton saveButton = new JButton("保存");
        
        addButton.addActionListener(e -> addNewProfile());
        deleteButton.addActionListener(e -> deleteSelectedProfile());
        saveButton.addActionListener(e -> saveCurrentProfile());
        
        listButtonPanel.add(addButton);
        listButtonPanel.add(deleteButton);
        listButtonPanel.add(saveButton);
        leftPanel.add(listButtonPanel, BorderLayout.SOUTH);
        
        panel.add(leftPanel, BorderLayout.WEST);
        
        // 右侧：配置详情表单
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // LLM 类型选择
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        JBLabel typeLabel = new JBLabel("LLM 类型:");
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(typeLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        llmTypeComboBox = new JComboBox<>(LlmPreset.values());
        llmTypeComboBox.setToolTipText("选择预设的 LLM 服务类型，将自动填充模型名称");
        llmTypeComboBox.addActionListener(e -> onLlmTypeSelected());
        formPanel.add(llmTypeComboBox, gbc);
        row++;
        
        // Model
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        formPanel.add(new JBLabel("模型名称:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        llmModelField = new JBTextField();
        formPanel.add(llmModelField, gbc);
        row++;
        
        // API Key
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        formPanel.add(new JBLabel("API Key:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        llmApiKeyField = new JBPasswordField();
        formPanel.add(llmApiKeyField, gbc);
        row++;
        
        // API URL
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        formPanel.add(new JBLabel("API URL:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        llmApiUrlField = new JBTextField();
        llmApiUrlField.setToolTipText("请手动输入 API 端点地址，例如：https://api.deepseek.com/v1/chat/completions");
        formPanel.add(llmApiUrlField, gbc);
        row++;
        
        // 说明文字
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(15, 5, 5, 5);
        JBLabel helpLabel = new JBLabel("<html><body style='color: gray;'>" +
                "<b>使用说明:</b><br>" +
                "1. 从「LLM 类型」下拉框选择预设服务（推荐使用<span style='color: #2196F3;'>免费服务</span>）<br>" +
                "2. 填写对应服务的 API Key<br>" +
                "3. 模型名称会根据选择的类型自动填充，也可手动修改<br>" +
                "4. <b>API URL 需要手动输入</b>，请根据您选择的服务提供商填写正确的 API 端点地址<br>" +
                "5. 可创建多个档案用于不同场景<br>" +
                "<br><b>推荐免费服务:</b> 阿里云百炼、DeepSeek、通义千问、智谱AI、SiliconFlow" +
                "</body></html>");
        formPanel.add(helpLabel, gbc);
        
        // 添加空白区域
        gbc.gridy = row + 1;
        gbc.weighty = 1.0;
        formPanel.add(Box.createVerticalGlue(), gbc);
        
        JBScrollPane formScrollPane = new JBScrollPane(formPanel);
        formScrollPane.setBorder(null);
        panel.add(formScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JComponent createRagPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(15));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 知识库路径
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("知识库路径:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        knowledgeBasePathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor kbDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        knowledgeBasePathField.addActionListener(e -> {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    kbDescriptor, project, null,
                    file -> knowledgeBasePathField.setText(file.getPath())
            );
        });
        panel.add(knowledgeBasePathField, gbc);
        
        // 课程材料路径
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("课程材料路径:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        courseMaterialPathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor cmDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        courseMaterialPathField.addActionListener(e -> {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    cmDescriptor, project, null,
                    file -> courseMaterialPathField.setText(file.getPath())
            );
        });
        panel.add(courseMaterialPathField, gbc);
        
        // 用户上传路径 - 隐藏此配置项，使用默认路径
        // 注意：虽然不在 UI 显示，但后端代码仍会使用配置服务中的默认路径
        
        // 上传文档到知识库按钮
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(15, 10, 10, 10);
        
        JButton uploadButton = new JButton("上传文档到知识库");
        uploadButton.setToolTipText("选择文档（PDF, PPT, PPTX, DOC, DOCX, TXT, MD）上传到RAG知识库");
        uploadButton.addActionListener(e -> uploadDocumentsToKnowledgeBase());
        panel.add(uploadButton, gbc);
        
        // 查看知识库按钮
        gbc.gridy = 3;
        gbc.insets = JBUI.insets(10, 10, 10, 10);
        JButton viewKnowledgeBaseButton = new JButton("查看知识库文件");
        viewKnowledgeBaseButton.setToolTipText("查看、删除或添加知识库中的文件");
        viewKnowledgeBaseButton.addActionListener(e -> openKnowledgeBaseManager());
        panel.add(viewKnowledgeBaseButton, gbc);
        
        // 添加说明文字
        gbc.gridy = 4;
        gbc.insets = JBUI.insets(5, 10, 10, 10);
        JBLabel uploadHelpLabel = new JBLabel("<html><body style='color: gray; font-size: 11px;'>" +
                "支持上传 PDF, PPT, PPTX, DOC, DOCX, TXT, MD 格式文档。可选择多个文件或文件夹。</body></html>");
        panel.add(uploadHelpLabel, gbc);
        
        // 重置为默认值按钮
        gbc.gridy = 5;
        gbc.insets = JBUI.insets(10, 10, 10, 10);
        JButton resetDefaultsButton = new JButton("重置为默认值");
        resetDefaultsButton.setToolTipText("将所有路径配置重置为默认值");
        resetDefaultsButton.addActionListener(e -> resetRagPathsToDefaults());
        panel.add(resetDefaultsButton, gbc);
        
        // 添加空白区域
        gbc.gridy = 6;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private JComponent createEmbeddingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(15));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Embedding 服务类型
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("服务类型:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        embeddingServiceTypeComboBox = new JComboBox<>(new String[]{
                "DashScope",
                "Zhipu",
                "Local"
        });
        embeddingServiceTypeComboBox.addActionListener(e -> updateEmbeddingApiKeyFieldState());
        panel.add(embeddingServiceTypeComboBox, gbc);
        
        // Embedding API Key
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        embeddingApiKeyLabel = new JBLabel("API Key:");
        panel.add(embeddingApiKeyLabel, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        embeddingApiKeyField = new JBPasswordField();
        embeddingApiKeyField.setToolTipText("Local 类型不需要 API Key");
        panel.add(embeddingApiKeyField, gbc);
        
        // 初始化 API Key 字段状态
        updateEmbeddingApiKeyFieldState();
        
        // 说明文字
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(10, 5, 5, 5);
        JBLabel embeddingHelpLabel = new JBLabel("<html><body style='color: gray; font-size: 11px;'>" +
                "<b>说明：</b><br>" +
                "• DashScope：阿里云百炼 Embedding 服务（推荐）<br>" +
                "• Zhipu：智谱AI Embedding 服务<br>" +
                "• Local：使用本地实现的embedding方法，无需 API Key（不准确，建议使用专业embedding服务）</body></html>");
        panel.add(embeddingHelpLabel, gbc);
        
        // 添加空白区域
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    /**
     * 根据 Embedding 服务类型更新 API Key 字段状态
     */
    private void updateEmbeddingApiKeyFieldState() {
        String serviceType = (String) embeddingServiceTypeComboBox.getSelectedItem();
        boolean isLocal = "Local".equals(serviceType);
        
        // 隐藏或显示 API Key 标签和输入框
        embeddingApiKeyLabel.setVisible(!isLocal);
        embeddingApiKeyField.setVisible(!isLocal);
        
        if (isLocal) {
            // Local 类型不需要 API Key，但不清空已有文本（保持用户设置）
            embeddingApiKeyField.setToolTipText("Local 类型不需要 API Key，此字段将被忽略");
        } else {
            embeddingApiKeyField.setEnabled(true);
            embeddingApiKeyField.setToolTipText("请输入 " + serviceType + " 的 API Key");
        }
    }
    
    private JComponent createRetrievalPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(15));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Top K
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("检索数量 (Top K):"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        retrievalTopKSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
        panel.add(retrievalTopKSpinner, gbc);
        
        // 相关度阈值
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("相关度阈值:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        relevanceThresholdSpinner = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05));
        panel.add(relevanceThresholdSpinner, gbc);
        
        // 说明文字
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(10, 5, 5, 5);
        JBLabel retrievalHelpLabel = new JBLabel("<html><body style='color: gray; font-size: 11px;'>" +
                "<b>说明：</b><br>" +
                "• 检索数量 (Top K)：从知识库中检索最相关的前 K 个文档片段<br>" +
                "• 相关度阈值：相似度低于此值的文档不会被使用。建议范围：0.3-0.5，默认 0.3<br>" +
                "&nbsp;&nbsp;&nbsp;<span>注意：阈值过高会导致检索不到相关材料。设置超过 0.5 时，实际使用时会被限制为 0.5</span></body></html>");
        panel.add(retrievalHelpLabel, gbc);
        
        // 重置为默认值按钮
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(10, 10, 10, 10);
        JButton resetRetrievalDefaultsButton = new JButton("重置为默认值");
        resetRetrievalDefaultsButton.setToolTipText("将检索参数重置为默认值（Top K: 5, 相关度阈值: 0.3）");
        resetRetrievalDefaultsButton.addActionListener(e -> resetRetrievalParamsToDefaults());
        panel.add(resetRetrievalDefaultsButton, gbc);
        
        // 添加空白区域
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
    }
    
    private void loadSettings() {
        ConfigService.Config config = configService.getState();
        if (config == null) {
            config = new ConfigService.Config();
        }
        
        // 加载 LLM 配置
        profiles.clear();
        profiles.addAll(config.llmProfiles);
        
        // 确保档案名称与模型名称一致
        for (ConfigService.LlmProfile profile : profiles) {
            if (profile.model != null && !profile.model.trim().isEmpty()) {
                profile.name = profile.model;
            } else if (profile.name == null || profile.name.trim().isEmpty()) {
                profile.name = "新配置档案";
            }
        }
        
        profileListModel.clear();
        for (ConfigService.LlmProfile profile : profiles) {
            profileListModel.addElement(profile.name);
        }
        
        // 恢复默认配置文件的选中状态
        if (config.defaultProfileName != null && !config.defaultProfileName.isEmpty()) {
            for (int i = 0; i < profiles.size(); i++) {
                if (config.defaultProfileName.equals(profiles.get(i).name)) {
                    profileList.setSelectedIndex(i);
                    loadSelectedProfile();
                    break;
                }
            }
        } else if (!profiles.isEmpty()) {
            // 如果没有默认配置，选中第一个
            profileList.setSelectedIndex(0);
            loadSelectedProfile();
        }
        
        // 加载 RAG 配置
        if (config.knowledgeBasePath != null) {
            knowledgeBasePathField.setText(config.knowledgeBasePath);
        }
        if (config.courseMaterialPath != null) {
            courseMaterialPathField.setText(config.courseMaterialPath);
        }
        // 用户上传路径不在 UI 显示，使用配置服务返回的默认值或已有配置
        // 如果配置中没有，会在需要时使用 ConfigService.getUserUploadPath() 返回默认路径
        
        // 加载 Embedding 配置
        if (config.embeddingServiceType != null) {
            embeddingServiceTypeComboBox.setSelectedItem(config.embeddingServiceType);
        }
        // 更新 API Key 字段状态（在设置文本之前）
        updateEmbeddingApiKeyFieldState();
        if (config.embeddingApiKey != null) {
            embeddingApiKeyField.setText(config.embeddingApiKey);
        }
        
        // 加载检索配置（使用默认值确保初始化时正确）
        int topK = config.retrievalTopK > 0 ? config.retrievalTopK : 5;
        double threshold = (config.relevanceThreshold > 0 && config.relevanceThreshold <= 1.0) 
                          ? config.relevanceThreshold : 0.3;
        retrievalTopKSpinner.setValue(topK);
        relevanceThresholdSpinner.setValue(threshold);
    }
    
    private void loadSelectedProfile() {
        int selectedIndex = profileList.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            ConfigService.LlmProfile profile = profiles.get(selectedIndex);
            llmApiKeyField.setText(profile.apiKey);
            llmApiUrlField.setText(profile.apiUrl);
            llmModelField.setText(profile.model);
            
            // 根据 API URL 或模型名称匹配预设类型
            for (LlmPreset preset : LlmPreset.values()) {
                if (preset.getDefaultApiUrl().equals(profile.apiUrl) || 
                    preset.getDefaultModel().equals(profile.model)) {
                    llmTypeComboBox.setSelectedItem(preset);
                    break;
                }
            }
        }
    }
    
    private void onLlmTypeSelected() {
        LlmPreset selectedPreset = (LlmPreset) llmTypeComboBox.getSelectedItem();
        if (selectedPreset != null && selectedPreset != LlmPreset.CUSTOM) {
            // 只自动填充模型名称，URL 需要用户手动输入
            llmModelField.setText(selectedPreset.getDefaultModel());
            // API URL 不自动填充，用户需要手动输入
        }
    }
    
    private void addNewProfile() {
        ConfigService.LlmProfile newProfile = new ConfigService.LlmProfile();
        
        LlmPreset selectedPreset = (LlmPreset) llmTypeComboBox.getSelectedItem();
        if (selectedPreset != null && selectedPreset != LlmPreset.CUSTOM) {
            newProfile.apiUrl = selectedPreset.getDefaultApiUrl();
            newProfile.model = selectedPreset.getDefaultModel();
        } else {
            newProfile.apiUrl = "";
            newProfile.model = "";
        }
        // 模型名称即为档案名称
        newProfile.name = newProfile.model != null && !newProfile.model.isEmpty() 
                ? newProfile.model 
                : "新配置档案";
        newProfile.apiKey = "";
        
        profiles.add(newProfile);
        profileListModel.addElement(newProfile.name);
        
        profileList.setSelectedIndex(profiles.size() - 1);
    }
    
    private void deleteSelectedProfile() {
        int selectedIndex = profileList.getSelectedIndex();
        if (selectedIndex >= 0) {
            int result = JOptionPane.showConfirmDialog(
                    mainPanel,
                    "确定要删除配置档案「" + profiles.get(selectedIndex).name + "」吗？",
                    "确认删除",
                    JOptionPane.YES_NO_OPTION
            );
            
            if (result == JOptionPane.YES_OPTION) {
                profiles.remove(selectedIndex);
                profileListModel.remove(selectedIndex);
                
                llmApiKeyField.setText("");
                llmApiUrlField.setText("");
                llmModelField.setText("");
            }
        }
    }
    
    private void saveCurrentProfile() {
        int selectedIndex = profileList.getSelectedIndex();
        if (selectedIndex >= 0) {
            ConfigService.LlmProfile profile = profiles.get(selectedIndex);
            String oldName = profile.name;
            
            profile.apiKey = new String(llmApiKeyField.getPassword());
            profile.apiUrl = llmApiUrlField.getText();
            profile.model = llmModelField.getText();
            
            // 模型名称即为档案名称
            String newName = profile.model != null && !profile.model.trim().isEmpty() 
                    ? profile.model.trim() 
                    : "新配置档案";
            profile.name = newName;

            if (!oldName.equals(profile.name)) {
                profileListModel.set(selectedIndex, profile.name);
            }
            
            JOptionPane.showMessageDialog(mainPanel, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    public boolean isModified() {
        ConfigService.Config config = configService.getState();
        if (config == null) {
            return true;
        }
        
        if (!profiles.equals(config.llmProfiles)) return true;
        if (!knowledgeBasePathField.getText().equals(config.knowledgeBasePath != null ? config.knowledgeBasePath : "")) return true;
        if (!courseMaterialPathField.getText().equals(config.courseMaterialPath != null ? config.courseMaterialPath : "")) return true;
        // 用户上传路径不在 UI 显示，不检查其修改状态
        
        // 检查 Embedding 配置
        String currentEmbeddingType = (String) embeddingServiceTypeComboBox.getSelectedItem();
        if (!currentEmbeddingType.equals(config.embeddingServiceType != null ? config.embeddingServiceType : "DashScope")) return true;
        if (!new String(embeddingApiKeyField.getPassword()).equals(config.embeddingApiKey != null ? config.embeddingApiKey : "")) return true;
        
        // 检查检索配置
        if (!retrievalTopKSpinner.getValue().equals(config.retrievalTopK)) return true;
        if (!relevanceThresholdSpinner.getValue().equals(config.relevanceThreshold)) return true;
        
        return false;
    }
    
    public void apply() {
        ConfigService.Config config = configService.getState();
        if (config == null) {
            config = new ConfigService.Config();
        }
        
        config.llmProfiles.clear();
        config.llmProfiles.addAll(profiles);
        // 设置默认配置文件名：使用当前选中的配置，如果没有选中则使用第一个配置
        int selectedIndex = profileList.getSelectedIndex();
        ConfigService.LlmProfile selectedProfile = null;
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            selectedProfile = profiles.get(selectedIndex);
            config.defaultProfileName = selectedProfile.name;
        } else if (!profiles.isEmpty()) {
            selectedProfile = profiles.get(0);
            config.defaultProfileName = selectedProfile.name;
        } else {
            config.defaultProfileName = null;
        }
        
        // 将当前选中的 Profile 信息同步到 config 的 LLM 配置字段（用于向后兼容和验证）
        if (selectedProfile != null) {
            config.llmApiKey = selectedProfile.apiKey != null ? selectedProfile.apiKey : "";
            config.llmApiEndpoint = selectedProfile.apiUrl != null ? selectedProfile.apiUrl : "";
            config.llmModel = selectedProfile.model != null ? selectedProfile.model : "";
        } else {
            // 如果没有选中任何 Profile，清空这些字段
            config.llmApiKey = "";
            config.llmApiEndpoint = "";
            config.llmModel = "";
        }
        
        config.knowledgeBasePath = knowledgeBasePathField.getText();
        config.courseMaterialPath = courseMaterialPathField.getText();
        // 用户上传路径：如果配置中已有值则保留，否则使用默认值
        if (config.userUploadPath == null || config.userUploadPath.isEmpty()) {
            config.userUploadPath = configService.getUserUploadPath();
        }
        
        config.embeddingServiceType = (String) embeddingServiceTypeComboBox.getSelectedItem();
        config.embeddingApiKey = new String(embeddingApiKeyField.getPassword());
        
        config.retrievalTopK = (Integer) retrievalTopKSpinner.getValue();
        config.relevanceThreshold = (Double) relevanceThresholdSpinner.getValue();
        
        configService.loadState(config);
    }
    
    public void reset() {
        loadSettings();
    }
    
    public JPanel getPanel() {
        return mainPanel;
    }
    
    /**
     * 上传文档到知识库
     * 复用 UploadToKnowledgeBaseAction 的逻辑
     */
    private void uploadDocumentsToKnowledgeBase() {
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

        // 设置文件过滤器
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

        // 按钮点击事件已经在 EDT 线程上
        // 直接在 EDT 线程上调用文件选择器
        try {
            VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, project, null);
            if (selectedFiles == null || selectedFiles.length == 0) {
                // 用户取消选择
                return;
            }
            
            handleFileSelection(selectedFiles);
        } catch (Exception e) {
            Messages.showErrorDialog(
                    mainPanel,
                    "选择文件时发生错误: " + e.getMessage(),
                    "错误"
            );
        }
    }
    
    /**
     * 处理已选择的文件
     */
    private void handleFileSelection(VirtualFile[] selectedFiles) {
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
                        
                        // 如果没有文件，显示提示并返回
                        if (totalFiles == 0) {
                            indicator.setFraction(1.0);
                            indicator.setText("完成");
                            com.intellij.openapi.application.ApplicationManager.getApplication()
                                    .invokeLater(() -> {
                                        Messages.showWarningDialog(
                                                mainPanel,
                                                "未选择任何文件或文件夹",
                                                "上传提示"
                                        );
                                    });
                            return;
                        }

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

                            boolean success = false;
                            String errorMessage = null;
                            try {
                                success = ragService.uploadFilesToKnowledgeBase(filesToUpload);
                            } catch (Exception ex) {
                                errorMessage = ex.getMessage();
                                System.err.println("上传文件时发生异常: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                            
                            if (success) {
                                resultMessage.append("✅ 成功上传 ")
                                        .append(filesToUpload.size())
                                        .append(" 个文件\n");
                                
                                // 显示上传路径和文件列表
                                String uploadPath = configService.getUserUploadPath();
                                resultMessage.append("\n   📁 保存路径: ").append(uploadPath).append("\n");
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
                                resultMessage.append("❌ 文件上传失败");
                                if (errorMessage != null) {
                                    resultMessage.append(": ").append(errorMessage);
                                }
                                resultMessage.append("\n");
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
                            
                            boolean success = false;
                            String errorMessage = null;
                            try {
                                success = ragService.uploadFolderToKnowledgeBase(folder);
                            } catch (Exception ex) {
                                errorMessage = ex.getMessage();
                                System.err.println("上传文件夹时发生异常: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                            
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
                                
                                // 显示上传路径
                                String uploadPath = configService.getUserUploadPath();
                                resultMessage.append("\n   📁 保存路径: ").append(uploadPath).append("\n");
                            } else {
                                resultMessage.append("❌ 文件夹处理失败: ")
                                        .append(folder.getName());
                                if (errorMessage != null) {
                                    resultMessage.append(": ").append(errorMessage);
                                }
                                resultMessage.append("\n");
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

                        // 在UI线程显示结果对话框并更新路径显示
                        boolean finalAllSuccess = allSuccess;
                        String finalMessage = resultMessage.toString();
                        // 获取实际上传使用的路径（从配置服务获取，确保是真实使用的路径）
                        String actualUploadPath = configService.getUserUploadPath();
                        // 如果路径为空，使用默认路径
                        if (actualUploadPath == null || actualUploadPath.isEmpty()) {
                            actualUploadPath = System.getProperty("user.home") + java.io.File.separator + ".mypilot" + java.io.File.separator + "userUploads";
                        }
                        String finalUploadPath = actualUploadPath;
                        com.intellij.openapi.application.ApplicationManager.getApplication()
                                .invokeLater(() -> {
                                    // 确保配置中也保存了这个路径
                                    ConfigService.Config config = configService.getState();
                                    if (config != null && 
                                        (config.userUploadPath == null || 
                                         config.userUploadPath.isEmpty() || 
                                         !config.userUploadPath.equals(finalUploadPath))) {
                                        configService.setUserUploadPath(finalUploadPath);
                                    }
                                    
                                    // 用户上传路径不在 UI 显示，配置已在 RagService.uploadFilesToKnowledgeBase() 中自动保存
                                    // 路径使用 ConfigService.getUserUploadPath() 返回的默认值或已配置的值
                                    
                                    if (finalAllSuccess) {
                                        Messages.showInfoMessage(
                                                mainPanel,
                                                finalMessage,
                                                "上传成功"
                                        );
                                    } else {
                                        Messages.showWarningDialog(
                                                mainPanel,
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
    
    /**
     * 打开知识库管理对话框
     */
    private void openKnowledgeBaseManager() {
        KnowledgeBaseManageDialog dialog = new KnowledgeBaseManageDialog(project);
        dialog.show();
    }
    
    /**
     * 重置检索参数为默认值
     */
    private void resetRetrievalParamsToDefaults() {
        retrievalTopKSpinner.setValue(5);
        relevanceThresholdSpinner.setValue(0.3);
        Messages.showInfoMessage(
                mainPanel,
                "检索参数已重置为默认值：\n" +
                "• 检索数量 (Top K): 5\n" +
                "• 相关度阈值: 0.3\n\n" +
                "请在应用设置后点击\"确定\"保存配置。",
                "重置成功"
        );
    }
    
    /**
     * 重置 RAG 路径配置为默认值
     */
    private void resetRagPathsToDefaults() {
        int result = JOptionPane.showConfirmDialog(
                mainPanel,
                "确定要将 RAG 路径配置重置为默认值吗？\n\n" +
                "默认值：\n" +
                "• 知识库路径：~/.mypilot/vector_index\n" +
                "• 课程材料路径：~/.mypilot/courseMaterials\n" +
                "• 用户上传路径：~/.mypilot/userUploads（使用默认路径，不在界面显示）\n\n" +
                "注意：重置路径配置不会清空知识库中的文件。如需清空知识库，请在\"查看知识库文件\"中删除。",
                "确认重置",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            // 询问是否同时清空知识库
            int clearResult = JOptionPane.showConfirmDialog(
                    mainPanel,
                    "是否同时清空知识库中的所有文件？\n\n" +
                    "• 是：清空所有知识库文件（包括用户上传的文件）\n" +
                    "• 否：只重置路径配置，保留知识库文件\n" +
                    "• 取消：取消重置操作",
                    "是否清空知识库",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            
            if (clearResult == JOptionPane.CANCEL_OPTION) {
                return; // 用户取消
            }
            
            String userHome = System.getProperty("user.home");
            String separator = File.separator;
            
            // 重置为默认值
            knowledgeBasePathField.setText(userHome + separator + ".mypilot" + separator + "vector_index");
            courseMaterialPathField.setText(userHome + separator + ".mypilot" + separator + "courseMaterials");
            // 用户上传路径不在 UI 显示，使用配置服务默认值
            // 配置会在保存时自动使用 ConfigService.getUserUploadPath() 返回的默认路径
            
            // 如果用户选择清空知识库
            if (clearResult == JOptionPane.YES_OPTION) {
                // 在后台线程中执行清空操作
                com.intellij.openapi.progress.ProgressManager.getInstance().run(
                    new com.intellij.openapi.progress.Task.Backgroundable(
                        project, "清空知识库", true) {

                        @Override
                        public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                            indicator.setIndeterminate(true);
                            indicator.setText("正在清空知识库...");

                            try {
                                com.javaee.mypilot.service.RagService ragService = 
                                    com.javaee.mypilot.service.RagService.getInstance(project);
                                
                                // 确保 RAG 服务已初始化（如果没有初始化，先初始化）
                                if (!ragService.isKnowledgeBaseInitialized()) {
                                    ragService.initialize();
                                }
                                
                                // 清空知识库（包括所有文件）
                                // clearKnowledgeBase() 会检查是否已初始化，所以这里可以安全调用
                                ragService.clearKnowledgeBase();

                                // 切换到 EDT 线程显示成功消息
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                    Messages.showInfoMessage(
                                            project,
                                            "路径配置已重置为默认值，知识库已清空。\n\n请在应用设置后点击\"确定\"保存配置。",
                                            "重置成功"
                                    );
                                }, com.intellij.openapi.application.ModalityState.any());
                            } catch (Exception e) {
                                System.err.println("清空知识库失败: " + e.getMessage());
                                e.printStackTrace();
                                
                                // 切换到 EDT 线程显示错误消息
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                                    Messages.showWarningDialog(
                                            project,
                                            "路径配置已重置为默认值，但清空知识库时出错: " + e.getMessage() + "\n\n请在应用设置后点击\"确定\"保存配置。",
                                            "部分成功"
                                    );
                                }, com.intellij.openapi.application.ModalityState.any());
                            }
                        }
                    }
                );
            } else {
                Messages.showInfoMessage(
                        mainPanel,
                        "路径配置已重置为默认值\n\n请在应用设置后点击\"确定\"保存配置。",
                        "重置成功"
                );
            }
        }
    }
}

