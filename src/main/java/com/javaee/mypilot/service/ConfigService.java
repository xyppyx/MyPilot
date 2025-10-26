package com.javaee.mypilot.service;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置服务类
 * 负责持久化存储和管理插件的配置信息
 * TODO: idea
 */
@State(
    name = "com.javaee.mypilot.service.ConfigService",
    storages = @Storage("MypilotConfig.xml")
)
@Service(Service.Level.PROJECT)
public final class ConfigService implements PersistentStateComponent<ConfigService.Config> {

    /**
     * LLM配置档案
     */
    public static class LlmProfile {
        public String name;
        public String apiKey;
        public String apiUrl;
        public String model;
    }

    /**
     * 配置数据结构
     * 用于持久化存储插件配置
     */
    public static class Config {
        public List<LlmProfile> llmProfiles = new ArrayList<>();
        public String ragSearchPath;
        public String defaultProfileName;
    }

    private Config myConfig = new Config();

    public static ConfigService getInstance(Project project) {
        return project.getService(ConfigService.class);
    }

    @Nullable
    @Override
    public Config getState() {
        return myConfig;
    }

    @Override
    public void loadState(@NotNull Config config) {
        myConfig = config;
    }

    public void addLlmProfile(LlmProfile profile) {
        myConfig.llmProfiles.add(profile);
    }

    public List<LlmProfile> getLlmProfiles() {
        return myConfig.llmProfiles;
    }

    public void setRagSearchPath(String path) {
        myConfig.ragSearchPath = path;
    }

    public String getRagSearchPath() {
        return myConfig.ragSearchPath;
    }
}
