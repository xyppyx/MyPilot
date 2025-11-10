package com.javaee.mypilot.core.model.edit;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Edit响应模型
 * 调用大语言模型后返回的数据结构
 */
public class EditResponse {

    private String explanation;                 // 语言解释部分

    @SerializedName("actions")
    private List<CodeAction> codeActions;       // 代码部分

    public EditResponse() {
    }

    public EditResponse(String explanation, List<CodeAction> codeActions, String completeCode) {
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
