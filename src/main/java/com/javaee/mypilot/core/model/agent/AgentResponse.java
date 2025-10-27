package com.javaee.mypilot.core.model.agent;

/**
 * Agent响应模型
 * 调用大语言模型后返回的数据结构
 */
public class AgentResponse {

    private String explanation;  // 语言解释部分
    private String code;         // 代码部分

    public AgentResponse() {
    }

    public AgentResponse(String explanation, String code) {
        this.explanation = explanation;
        this.code = code;
    }

    // Getters and Setters
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
