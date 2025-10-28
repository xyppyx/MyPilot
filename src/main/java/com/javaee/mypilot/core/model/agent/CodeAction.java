package com.javaee.mypilot.core.model.agent;

import com.javaee.mypilot.core.enums.CodeOpt;

/**
 * llm返回的代码操作模型
 */
public class CodeAction {

    private CodeOpt opt;            // 代码操作类型
    private String filePath;        // 代码文件路径
    private int startLine;          // 代码操作起始行
    private int endLine;            // 代码操作结束行
    private String oldCode;         // 旧代码内容
    private String newCode;         // 新代码内容

    public CodeAction() {
    }

    public CodeAction(CodeOpt opt, String filePath, int startLine, int endLine, String oldCode, String newCode) {
        this.opt = opt;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.oldCode = oldCode;
        this.newCode = newCode;
    }

    // Getters and Setters
    public CodeOpt getOpt() {
        return opt;
    }

    public void setOpt(CodeOpt opt) {
        this.opt = opt;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getOldCode() {
        return oldCode;
    }

    public void setOldCode(String oldCode) {
        this.oldCode = oldCode;
    }

    public String getNewCode() {
        return newCode;
    }

    public void setNewCode(String newCode) {
        this.newCode = newCode;
    }
}
