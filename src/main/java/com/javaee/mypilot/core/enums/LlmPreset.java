package com.javaee.mypilot.core.enums;

/**
 * LLM 服务预设配置
 * 提供常用免费 LLM 服务的预设配置
 */
public enum LlmPreset {
    
    CUSTOM("自定义", "", "", ""),
    
    // 阿里云百炼
    ALIYUN_BAILIAN(
            "阿里云百炼",
            "https://bailian.aliyuncs.com/api/v1/chat/completions",
            "qwen-plus",
            ""
    ),
    
    // DeepSeek
    DEEPSEEK(
            "DeepSeek",
            "https://api.deepseek.com/v1/chat/completions",
            "deepseek-chat",
            ""
    ),
    
    // 通义千问 (DashScope)
    QWEN_DASHSCOPE(
            "通义千问",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen-plus",
            ""
    ),
    
    // 智谱 AI
    ZHIPU_AI(
            "智谱 AI",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-4-flash",
            ""
    ),
    
    // 月之暗面 Kimi
    MOONSHOT(
            "Kimi",
            "https://api.moonshot.cn/v1/chat/completions",
            "moonshot-v1-8k",
            ""
    ),
    
    // 百度文心一言
    BAIDU_WENXIN(
            "文心一言",
            "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
            "ernie-4.0-8k-latest",
            ""
    );
    
    private final String displayName;
    private final String defaultApiUrl;
    private final String defaultModel;
    private final String description;
    
    LlmPreset(String displayName, String defaultApiUrl, String defaultModel, String description) {
        this.displayName = displayName;
        this.defaultApiUrl = defaultApiUrl;
        this.defaultModel = defaultModel;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDefaultApiUrl() {
        return defaultApiUrl;
    }
    
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
    
    /**
     * 判断是否为免费服务
     */
    public boolean isFree() {
        return this != CUSTOM;  // 除了自定义，其他都有免费额度
    }
    
    /**
     * 判断是否推荐使用
     */
    public boolean isRecommended() {
        return this == ALIYUN_BAILIAN || 
               this == DEEPSEEK || 
               this == QWEN_DASHSCOPE || 
               this == ZHIPU_AI;
    }
}

