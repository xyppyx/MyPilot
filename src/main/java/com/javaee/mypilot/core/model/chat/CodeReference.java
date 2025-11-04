package com.javaee.mypilot.core.model.chat;

/**
 * 代码引用信息 - 用于标识用户选中的代码片段, 传递到rag/agent中调用psi获取上下文
 * Code reference information for identifying the code snippet selected by the user
 */
public class CodeReference {

    private String virtualFileUrl;      // 文件的唯一路径/URL
    private int startOffset;            // 选中代码的起始字符偏移量
    private int endOffset;              // 选中代码的结束字符偏移量
    private int startLine;              // 选中代码的起始行号
    private int endLine;                // 选中代码的结束行号
    private String selectedCode;        // 选中的代码文本（用于展示和初步 RAG）

    public String toString() {
        return "CodeReference{" +
                "virtualFileUrl='" + virtualFileUrl + '\'' +
                ", startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                ", startLine=" + startLine +
                ", endLine=" + endLine + '}' +
                "\n\nselectedCode='''\n" + selectedCode + "\n'''\n";
    }

    // Getters and Setters
    public String getVirtualFileUrl() {
        return virtualFileUrl;
    }

    public void setVirtualFileUrl(String virtualFileUrl) {
        this.virtualFileUrl = virtualFileUrl;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public String getSelectedCode() {
        return selectedCode;
    }

    public void setSelectedCode(String selectedCode) {
        this.selectedCode = selectedCode;
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
}
