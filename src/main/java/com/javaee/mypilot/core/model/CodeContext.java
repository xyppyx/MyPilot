package com.javaee.mypilot.core.model;

/**
 * 代码上下文信息 - 用于传递用户当前编辑的代码上下文
 * Code context information for understanding user's current coding context
 */
public class CodeContext {
    private String selectedCode;        // 选中的代码
    private String fileName;            // 文件名
    private String packageName;         // 包名
    private String className;           // 类名
    private String methodName;          // 方法名
    private int lineNumber;             // 行号
    private String surroundingCode;     // 周围的代码（上下文）

    public CodeContext() {
    }

    // Getters and Setters
    public String getSelectedCode() {
        return selectedCode;
    }

    public void setSelectedCode(String selectedCode) {
        this.selectedCode = selectedCode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getSurroundingCode() {
        return surroundingCode;
    }

    public void setSurroundingCode(String surroundingCode) {
        this.surroundingCode = surroundingCode;
    }

    /**
     * 格式化代码上下文为可读字符串
     */
    public String formatContext() {
        StringBuilder sb = new StringBuilder();
        if (fileName != null) {
            sb.append("File: ").append(fileName).append("\n");
        }
        if (className != null) {
            sb.append("Class: ").append(className).append("\n");
        }
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n");
        }
        if (selectedCode != null && !selectedCode.isEmpty()) {
            sb.append("\nSelected Code:\n```java\n").append(selectedCode).append("\n```");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "CodeContext{" +
                "fileName='" + fileName + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", lineNumber=" + lineNumber +
                '}';
    }
}


