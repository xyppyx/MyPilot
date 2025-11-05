package com.javaee.mypilot.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
    public String defaultProfileName;
        public String knowledgeBasePath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "vector_index";
        public String courseMaterialPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "courseMaterials"; // 课程材料文件夹路径（PPT/PDF）
        public String userUploadPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "userUploads"; // 用户上传的文档路径

        // RAG Embedding 配置
        public String embeddingServiceType = ""; // DashScope, Zhipu, Local
        public String embeddingApiKey = ""; // 用户需要自行配置


        // RAG 检索配置
        public int retrievalTopK = 5;
        public double relevanceThreshold = 0.3;

        // LLM API 配置
        public String llmApiType = ""; // DeepSeek, OpenAI, QianWen, ZhiPu, etc.
        public String llmApiKey = ""; // 用户需要自行配置
        public String llmApiEndpoint = ""; // 用户需要自行配置
        public String llmModel = ""; // 用户需要自行配置
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
        // 如果某些字段为空，使用默认值
        if (config.knowledgeBasePath == null || config.knowledgeBasePath.isEmpty()) {
            config.knowledgeBasePath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "vector_index";
        }
        if (config.courseMaterialPath == null || config.courseMaterialPath.isEmpty()) {
            config.courseMaterialPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "courseMaterials";
        }
        if (config.userUploadPath == null || config.userUploadPath.isEmpty()) {
            config.userUploadPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "userUploads";
        }
        if (config.embeddingServiceType == null || config.embeddingServiceType.isEmpty()) {
            config.embeddingServiceType = "DashScope";
        }
        if (config.embeddingApiKey == null) {
            config.embeddingApiKey = "";
        }
        // LLM API 配置空值处理
        if (config.llmApiType == null || config.llmApiType.isEmpty()) {
            config.llmApiType = "";
        }
        if (config.llmApiKey == null) {
            config.llmApiKey = "";
        }
        if (config.llmApiEndpoint == null) {
            config.llmApiEndpoint = "";
        }
        if (config.llmModel == null) {
            config.llmModel = "";
        }
        // 确保检索参数使用合理的默认值
        if (config.retrievalTopK <= 0) {
            config.retrievalTopK = 5;
        }
        // 如果阈值 <=0，重置为默认值 0.3
        // 注意：>0.5 的值也允许，实际使用时会在 RagService 中被限制为 0.5
        if (config.relevanceThreshold <= 0) {
            config.relevanceThreshold = 0.3;
        }
        
        myConfig = config;
    }

    public void addLlmProfile(LlmProfile profile) {
        myConfig.llmProfiles.add(profile);
    }

    public List<LlmProfile> getLlmProfiles() {
        return myConfig.llmProfiles;
    }

    public void setKnowledgeBasePath(String path) {
        myConfig.knowledgeBasePath = path;
    }

    public String getKnowledgeBasePath() {
        return myConfig.knowledgeBasePath;
    }

    public void setEmbeddingServiceType(String type) {
        myConfig.embeddingServiceType = type;
    }

    public String getEmbeddingServiceType() {
        return myConfig.embeddingServiceType;
    }

    public void setEmbeddingApiKey(String apiKey) {
        myConfig.embeddingApiKey = apiKey;
    }

    public String getEmbeddingApiKey() {
        return myConfig.embeddingApiKey;
    }

    public void setRetrievalTopK(int topK) {
        myConfig.retrievalTopK = topK;
    }

    public int getRetrievalTopK() {
        return myConfig.retrievalTopK;
    }

    public void setRelevanceThreshold(double threshold) {
        myConfig.relevanceThreshold = threshold;
    }

    public double getRelevanceThreshold() {
        return myConfig.relevanceThreshold;
    }

    public void setCourseMaterialPath(String path) {
        myConfig.courseMaterialPath = path;
    }

    public String getCourseMaterialPath() {
        return myConfig.courseMaterialPath;
    }

    public void setUserUploadPath(String path) {
        myConfig.userUploadPath = path;
    }

    public String getUserUploadPath() {
        // 确保总是返回有效的路径，如果配置为空，返回默认路径
        if (myConfig.userUploadPath == null || myConfig.userUploadPath.isEmpty()) {
            return System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "userUploads";
        }
        return myConfig.userUploadPath;
    }

    public String getLlmApiType() {
        return myConfig.llmApiType;
    }

    public void setLlmApiType(String type) {
        myConfig.llmApiType = type;
    }

    public String getLlmApiKey() {
        return myConfig.llmApiKey;
    }

    public void setLlmApiKey(String apiKey) {
        myConfig.llmApiKey = apiKey;
    }

    public String getLlmApiEndpoint() {
        return myConfig.llmApiEndpoint;
    }

    public void setLlmApiEndpoint(String endpoint) {
        myConfig.llmApiEndpoint = endpoint;
    }

    public String getLlmModel() {
        return myConfig.llmModel;
    }

    public void setLlmModel(String model) {
        myConfig.llmModel = model;
    }

    /**
     * 验证 LLM 配置是否完整
     * @return 如果配置不完整，返回错误信息；如果配置完整，返回 null
     */
    @Nullable
    public String validateLlmConfig() {
        if (myConfig.llmApiKey == null || myConfig.llmApiKey.trim().isEmpty()) {
            return "LLM API Key 未配置，请在 Settings → Tools → MyPilot 中配置";
        }
        if (myConfig.llmApiEndpoint == null || myConfig.llmApiEndpoint.trim().isEmpty()) {
            return "LLM API Endpoint 未配置，请在 Settings → Tools → MyPilot 中配置";
        }
        if (myConfig.llmModel == null || myConfig.llmModel.trim().isEmpty()) {
            return "LLM Model 未配置，请在 Settings → Tools → MyPilot 中配置";
        }
        return null;
    }

    /**
     * 验证 Embedding 配置是否完整
     * @return 如果配置不完整，返回错误信息；如果配置完整，返回 null
     */
    @Nullable
    public String validateEmbeddingConfig() {
        String serviceType = myConfig.embeddingServiceType;
        if (serviceType == null || serviceType.trim().isEmpty()) {
            return "Embedding 未配置，请在 Settings → Tools → MyPilot 中配置";
        }
        // Local 类型不需要 API Key
        if (!"Local".equals(serviceType)) {
            if (myConfig.embeddingApiKey == null || myConfig.embeddingApiKey.trim().isEmpty()) {
                return "Embedding API Key 未配置，请在 Settings → Tools → MyPilot 中配置";
            }
        }
        return null;
    }
}
