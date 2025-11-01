package com.javaee.mypilot.view.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.service.ConfigService;
import com.javaee.mypilot.service.RagService;
import com.javaee.mypilot.view.ui.MyPilotSettingsPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * MyPilot 配置页面（Settings → Tools → MyPilot）
 * 
 * 提供以下配置功能：
 * 1. LLM 配置档案管理（API Key、URL、Model）
 * 2. RAG 配置（知识库路径、课程材料路径）
 * 3. Embedding 服务配置
 * 4. 检索参数配置
 */
public class MyPilotConfigurable implements Configurable {
    
    private final Project project;
    private MyPilotSettingsPanel settingsPanel;
    private final ConfigService configService;
    
    public MyPilotConfigurable(Project project) {
        this.project = project;
        this.configService = ConfigService.getInstance(project);
    }
    
    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "MyPilot";
    }
    
    @Nullable
    @Override
    public String getHelpTopic() {
        return "settings.mypilot";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        if (settingsPanel == null) {
            settingsPanel = new MyPilotSettingsPanel(project);
        }
        return settingsPanel.getPanel();
    }
    
    @Override
    public boolean isModified() {
        return settingsPanel != null && settingsPanel.isModified();
    }
    
    @Override
    public void apply() throws ConfigurationException {
        if (settingsPanel != null) {
            // 保存旧的 embedding 配置，用于检测是否更改
            ConfigService.Config oldConfig = configService.getState();
            String oldEmbeddingType = oldConfig != null ? oldConfig.embeddingServiceType : null;
            String oldEmbeddingApiKey = oldConfig != null ? oldConfig.embeddingApiKey : null;
            String oldKnowledgeBasePath = oldConfig != null ? oldConfig.knowledgeBasePath : null;
            
            // 应用新配置
            settingsPanel.apply();
            
            // 获取新配置
            ConfigService.Config newConfig = configService.getState();
            String newEmbeddingType = newConfig != null ? newConfig.embeddingServiceType : null;
            String newEmbeddingApiKey = newConfig != null ? newConfig.embeddingApiKey : null;
            String newKnowledgeBasePath = newConfig != null ? newConfig.knowledgeBasePath : null;
            
            // 检查 embedding 配置或知识库路径是否更改，如果是则重新初始化 RagService
            boolean embeddingChanged = (oldEmbeddingType != null && !oldEmbeddingType.equals(newEmbeddingType)) ||
                                      (oldEmbeddingApiKey != null && !oldEmbeddingApiKey.equals(newEmbeddingApiKey));
            boolean knowledgeBasePathChanged = (oldKnowledgeBasePath != null && !oldKnowledgeBasePath.equals(newKnowledgeBasePath));
            
            if (embeddingChanged || knowledgeBasePathChanged) {
                // 使用 ApplicationManager 在后台线程中重新初始化，避免阻塞 UI
                // 这样可以确保在正确的线程上下文中执行
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        RagService ragService = RagService.getInstance(project);
                        ragService.reinitialize();
                    } catch (Exception e) {
                        System.err.println("重新初始化 RagService 失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
    }
    
    @Override
    public void reset() {
        if (settingsPanel != null) {
            settingsPanel.reset();
        }
    }
    
    @Override
    public void disposeUIResources() {
        settingsPanel = null;
    }
}

