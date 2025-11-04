package com.javaee.mypilot.core.model.agent;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Agent响应模型
 * 调用大语言模型后返回的数据结构
 */
public class AgentResponse {

    private String explanation;                 // 语言解释部分

    @SerializedName("actions")
    private List<CodeAction> codeActions;       // 代码部分

    public AgentResponse() {
    }

    public AgentResponse(String explanation, List<CodeAction> codeActions, String completeCode) {
        this.explanation = explanation;
        this.codeActions = codeActions;
    }

    // Getters and Setters
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<CodeAction> getCodeActions() {
        return codeActions;
    }

    public void setCodeActions(List<CodeAction> codeActions) {
        this.codeActions = codeActions;
    }
}
