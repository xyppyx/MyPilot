package com.javaee.mypilot.view.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.service.ConfigService;
import com.javaee.mypilot.view.settings.MyPilotSettingsPanel;
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
            settingsPanel.apply();
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

