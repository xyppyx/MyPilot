package com.javaee.mypilot.core.model.chat;

/**
 * 代码引用信息 - 用于标识用户选中的代码片段, 传递到rag/edit中调用psi获取上下文
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
        String metaData = "选中代码信息{" +
                "virtualFileUrl='" + virtualFileUrl + '\'' +
                "\nstartOffset=" + startOffset +
                "\nendOffset=" + endOffset +
                "\nstartLine=" + startLine +
                "\nendLine=" + endLine +
                "\n}" +
                "\n以下用'''包裹的部分是选择的代码（行号为原始文件行号）\n'''\n";

        String numberedCode = addLineNumbers(selectedCode, startLine);
        return metaData + numberedCode + "\n'''\n";
    }

    /**
     * 辅助方法：将代码字符串转换为带行号的字符串
     * @param code 代码字符串
     * @param startLine 起始行号
     * @return 带行号的代码字符串
     */
    private String addLineNumbers(String code, int startLine) {
        if (code == null || code.isEmpty()) {
            return "";
        }

        StringBuilder numberedCode = new StringBuilder();
        // 按行分割代码。使用 \r?\n 来兼容不同系统的换行符。
        String[] lines = code.split("\r?\n", -1); // -1 确保末尾的空行也被保留

        int currentLine = startLine;

        // 找出最大的行号的长度，用于对齐
        int maxLineLength = String.valueOf(startLine + lines.length - 1).length();

        for (int i = 0; i < lines.length; i++) {
            // 使用 String.format 进行右对齐和填充空格，确保美观
            // 例如："%3d: " 会将行号填充到至少 3 位宽
            numberedCode.append(String.format("%" + maxLineLength + "d: ", currentLine));

            // 拼接代码行。对于最后一行，如果原代码以换行符结束，lines数组的最后一个元素是空字符串
            numberedCode.append(lines[i]);

            // 仅在非最后一行或代码本身不以换行符结束时添加换行符
            // 原始 selectedCode 中的换行符在 split 时已经被消耗
            if (i < lines.length - 1) {
                numberedCode.append("\n");
            }

            currentLine++;
        }

        return numberedCode.toString();
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
