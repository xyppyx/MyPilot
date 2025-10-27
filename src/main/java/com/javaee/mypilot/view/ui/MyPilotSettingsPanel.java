package com.javaee.mypilot.view.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.javaee.mypilot.core.enums.LlmPreset;
import com.javaee.mypilot.service.ConfigService;

import javax.swing.*;
import java.awt.*;
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
    private TextFieldWithBrowseButton ragSearchPathField;
    private TextFieldWithBrowseButton knowledgeBasePathField;
    private TextFieldWithBrowseButton courseMaterialPathField;
    
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
        
        // RAG 搜索路径
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("RAG 搜索路径:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        ragSearchPathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor ragDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        ragSearchPathField.addActionListener(e -> {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    ragDescriptor, project, null,
                    file -> ragSearchPathField.setText(file.getPath())
            );
        });
        panel.add(ragSearchPathField, gbc);
        
        // 知识库路径
        gbc.gridx = 0;
        gbc.gridy = 1;
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
        gbc.gridy = 2;
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
        
        // 添加空白区域
        gbc.gridy = 3;
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
        panel.add(embeddingServiceTypeComboBox, gbc);
        
        // Embedding API Key
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JBLabel("API Key:"), gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        embeddingApiKeyField = new JBPasswordField();
        panel.add(embeddingApiKeyField, gbc);
        
        // 添加空白区域
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
        
        return panel;
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
        panel.add(relevanceThresholdSpinner, gbc);
        
        // 添加空白区域
        gbc.gridy = 2;
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
        if (config.ragSearchPath != null) {
            ragSearchPathField.setText(config.ragSearchPath);
        }
        if (config.knowledgeBasePath != null) {
            knowledgeBasePathField.setText(config.knowledgeBasePath);
        }
        if (config.courseMaterialPath != null) {
            courseMaterialPathField.setText(config.courseMaterialPath);
        }
        
        // 加载 Embedding 配置
        if (config.embeddingServiceType != null) {
            embeddingServiceTypeComboBox.setSelectedItem(config.embeddingServiceType);
        }
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
        if (!ragSearchPathField.getText().equals(config.ragSearchPath != null ? config.ragSearchPath : "")) return true;
        if (!knowledgeBasePathField.getText().equals(config.knowledgeBasePath != null ? config.knowledgeBasePath : "")) return true;
        if (!courseMaterialPathField.getText().equals(config.courseMaterialPath != null ? config.courseMaterialPath : "")) return true;
        
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
        
        config.ragSearchPath = ragSearchPathField.getText();
        config.knowledgeBasePath = knowledgeBasePathField.getText();
        config.courseMaterialPath = courseMaterialPathField.getText();
        
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
}

