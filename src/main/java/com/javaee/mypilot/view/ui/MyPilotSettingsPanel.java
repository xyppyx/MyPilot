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
    private JBTextField llmProfileNameField;
    private JBPasswordField llmApiKeyField;
    private JBTextField llmApiUrlField;
    private JBTextField llmModelField;
    private JComboBox<String> defaultProfileComboBox;
    private DefaultListModel<String> profileListModel;
    private JList<String> profileList;
    private List<ConfigService.LlmProfile> profiles;
    
    // RAG 配置
    private TextFieldWithBrowseButton knowledgeBasePathField;
    private TextFieldWithBrowseButton courseMaterialPathField;
    private TextFieldWithBrowseButton userUploadPathField;
    
    // Embedding 配置
    private JComboBox<String> embeddingServiceTypeComboBox;
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
        llmTypeComboBox.setToolTipText("选择预设的 LLM 服务类型，将自动填充 API URL 和模型");
        llmTypeComboBox.addActionListener(e -> onLlmTypeSelected());
        formPanel.add(llmTypeComboBox, gbc);
        row++;
        
        
        // 档案名称
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        formPanel.add(new JBLabel("档案名称:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        llmProfileNameField = new JBTextField();
        formPanel.add(llmProfileNameField, gbc);
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
        formPanel.add(llmApiUrlField, gbc);
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
        
        // 默认档案选择
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        formPanel.add(new JBLabel("默认档案:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        defaultProfileComboBox = new JComboBox<>();
        formPanel.add(defaultProfileComboBox, gbc);
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
                "3. 点击「保存」保存配置<br>" +
                "4. 可创建多个档案用于不同场景<br>" +
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
        
        // 用户上传路径
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("用户上传路径:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        userUploadPathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor upDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        userUploadPathField.addActionListener(e -> {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    upDescriptor, project, null,
                    file -> userUploadPathField.setText(file.getPath())
            );
        });
        userUploadPathField.setToolTipText("用户上传的文档将存储在此路径下");
        panel.add(userUploadPathField, gbc);
        
        // 上传文档到知识库按钮
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(15, 10, 10, 10);
        
        JButton uploadButton = new JButton("📤 上传文档到知识库");
        uploadButton.setToolTipText("选择文档（PDF, PPT, PPTX, DOC, DOCX, TXT, MD）上传到RAG知识库");
        uploadButton.addActionListener(e -> uploadDocumentsToKnowledgeBase());
        panel.add(uploadButton, gbc);
        
        // 添加说明文字
        gbc.gridy = 4;
        gbc.insets = JBUI.insets(5, 10, 10, 10);
        JBLabel uploadHelpLabel = new JBLabel("<html><body style='color: gray; font-size: 11px;'>" +
                "支持上传 PDF, PPT, PPTX, DOC, DOCX, TXT, MD 格式文档。可选择多个文件或文件夹。</body></html>");
        panel.add(uploadHelpLabel, gbc);
        
        // 重置为默认值按钮
        gbc.gridy = 5;
        gbc.insets = JBUI.insets(10, 10, 10, 10);
        JButton resetDefaultsButton = new JButton("🔄 重置为默认值");
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
        panel.add(new JBLabel("API Key:"), gbc);
        
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
                "• Local：本地模型，无需 API Key</body></html>");
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
        embeddingApiKeyField.setEnabled(!isLocal);
        if (isLocal) {
            // Local 类型不需要 API Key，但不清空已有文本（保持用户设置）
            embeddingApiKeyField.setToolTipText("Local 类型不需要 API Key，此字段将被忽略");
        } else {
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
        relevanceThresholdSpinner = new JSpinner(new SpinnerNumberModel(0.7, 0.0, 1.0, 0.05));
        relevanceThresholdSpinner.setToolTipText("文档相似度低于此值将不使用知识库内容，默认 0.7");
        panel.add(relevanceThresholdSpinner, gbc);
        
        // 说明文字
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = JBUI.insets(10, 5, 5, 5);
        JBLabel retrievalHelpLabel = new JBLabel("<html><body style='color: gray; font-size: 11px;'>" +
                "<b>说明：</b><br>" +
                "• 检索数量 (Top K)：从知识库中检索最相关的前 K 个文档片段<br>" +
                "• 相关度阈值：相似度低于此值的文档不会被使用，默认 0.7</body></html>");
        panel.add(retrievalHelpLabel, gbc);
        
        // 添加空白区域
        gbc.gridy = 3;
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
        
        profileListModel.clear();
        defaultProfileComboBox.removeAllItems();
        for (ConfigService.LlmProfile profile : profiles) {
            profileListModel.addElement(profile.name);
            defaultProfileComboBox.addItem(profile.name);
        }
        
        if (config.defaultProfileName != null) {
            defaultProfileComboBox.setSelectedItem(config.defaultProfileName);
        }
        
        // 加载 RAG 配置
        if (config.knowledgeBasePath != null) {
            knowledgeBasePathField.setText(config.knowledgeBasePath);
        }
        if (config.courseMaterialPath != null) {
            courseMaterialPathField.setText(config.courseMaterialPath);
        }
        // 加载用户上传路径，如果为空则使用配置服务返回的默认值
        String userUploadPath = config.userUploadPath;
        if (userUploadPath == null || userUploadPath.isEmpty()) {
            userUploadPath = configService.getUserUploadPath(); // 这会返回默认路径如果配置为空
        }
        userUploadPathField.setText(userUploadPath);
        
        // 加载 Embedding 配置
        if (config.embeddingServiceType != null) {
            embeddingServiceTypeComboBox.setSelectedItem(config.embeddingServiceType);
        }
        // 更新 API Key 字段状态（在设置文本之前）
        updateEmbeddingApiKeyFieldState();
        if (config.embeddingApiKey != null) {
            embeddingApiKeyField.setText(config.embeddingApiKey);
        }
        
        // 加载检索配置
        retrievalTopKSpinner.setValue(config.retrievalTopK);
        relevanceThresholdSpinner.setValue(config.relevanceThreshold);
    }
    
    private void loadSelectedProfile() {
        int selectedIndex = profileList.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            ConfigService.LlmProfile profile = profiles.get(selectedIndex);
            llmProfileNameField.setText(profile.name);
            llmApiKeyField.setText(profile.apiKey);
            llmApiUrlField.setText(profile.apiUrl);
            llmModelField.setText(profile.model);
        }
    }
    
    private void onLlmTypeSelected() {
        LlmPreset selectedPreset = (LlmPreset) llmTypeComboBox.getSelectedItem();
        if (selectedPreset != null && selectedPreset != LlmPreset.CUSTOM) {
            llmApiUrlField.setText(selectedPreset.getDefaultApiUrl());
            llmModelField.setText(selectedPreset.getDefaultModel());
            
            String currentName = llmProfileNameField.getText();
            if (currentName == null || currentName.trim().isEmpty() || currentName.equals("新配置档案")) {
                llmProfileNameField.setText(selectedPreset.getDisplayName());
            }
        }
    }
    
    private void addNewProfile() {
        ConfigService.LlmProfile newProfile = new ConfigService.LlmProfile();
        
        LlmPreset selectedPreset = (LlmPreset) llmTypeComboBox.getSelectedItem();
        if (selectedPreset != null && selectedPreset != LlmPreset.CUSTOM) {
            newProfile.name = selectedPreset.getDisplayName();
            newProfile.apiUrl = selectedPreset.getDefaultApiUrl();
            newProfile.model = selectedPreset.getDefaultModel();
        } else {
            newProfile.name = "新配置档案";
            newProfile.apiUrl = "";
            newProfile.model = "";
        }
        newProfile.apiKey = "";
        
        profiles.add(newProfile);
        profileListModel.addElement(newProfile.name);
        defaultProfileComboBox.addItem(newProfile.name);
        
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
                String removedName = profiles.get(selectedIndex).name;
                profiles.remove(selectedIndex);
                profileListModel.remove(selectedIndex);
                defaultProfileComboBox.removeItem(removedName);
                
                llmProfileNameField.setText("");
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
            
            profile.name = llmProfileNameField.getText();
            profile.apiKey = new String(llmApiKeyField.getPassword());
            profile.apiUrl = llmApiUrlField.getText();
            profile.model = llmModelField.getText();
            
            profileListModel.set(selectedIndex, profile.name);
            
            if (!oldName.equals(profile.name)) {
                defaultProfileComboBox.removeItem(oldName);
                defaultProfileComboBox.addItem(profile.name);
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
        if (!userUploadPathField.getText().equals(config.userUploadPath != null ? config.userUploadPath : "")) return true;
        
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
        config.defaultProfileName = (String) defaultProfileComboBox.getSelectedItem();
        
        config.knowledgeBasePath = knowledgeBasePathField.getText();
        config.courseMaterialPath = courseMaterialPathField.getText();
        config.userUploadPath = userUploadPathField.getText();
        
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
                                    
                                    // 从配置服务重新读取路径（确保使用最新的配置值）
                                    String latestUploadPath = configService.getUserUploadPath();
                                    if (latestUploadPath == null || latestUploadPath.isEmpty()) {
                                        latestUploadPath = finalUploadPath;
                                    }
                                    
                                    // 更新UI字段显示实际使用的路径
                                    String currentText = userUploadPathField.getText();
                                    if (!latestUploadPath.equals(currentText)) {
                                        userUploadPathField.setText(latestUploadPath);
                                        // 强制刷新UI组件
                                        userUploadPathField.revalidate();
                                        userUploadPathField.repaint();
                                        // 确保父容器也刷新
                                        if (userUploadPathField.getParent() != null) {
                                            userUploadPathField.getParent().revalidate();
                                            userUploadPathField.getParent().repaint();
                                        }
                                    }
                                    
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
     * 重置 RAG 路径配置为默认值
     */
    private void resetRagPathsToDefaults() {
        int result = JOptionPane.showConfirmDialog(
                mainPanel,
                "确定要将所有 RAG 路径配置重置为默认值吗？\n\n" +
                "默认值：\n" +
                "• 知识库路径：~/.mypilot/vector_index\n" +
                "• 课程材料路径：~/.mypilot/courseMaterials\n" +
                "• 用户上传路径：~/.mypilot/userUploads",
                "确认重置",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            String userHome = System.getProperty("user.home");
            String separator = File.separator;
            
            // 重置为默认值
            knowledgeBasePathField.setText(userHome + separator + ".mypilot" + separator + "vector_index");
            courseMaterialPathField.setText(userHome + separator + ".mypilot" + separator + "courseMaterials");
            userUploadPathField.setText(userHome + separator + ".mypilot" + separator + "userUploads");
            
            Messages.showInfoMessage(
                    mainPanel,
                    "路径配置已重置为默认值\n\n请在应用设置后点击\"确定\"保存配置。",
                    "重置成功"
            );
        }
    }
}

