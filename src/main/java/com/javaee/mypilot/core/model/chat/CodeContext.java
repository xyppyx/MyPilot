package com.javaee.mypilot.core.model.chat;

/**
 * 代码上下文信息 - 用于传递用户当前编辑的代码上下文
 * Code context information for understanding user's current coding context
 */
public class CodeContext {

    private CodeReference sourceReference;
    private String fileName;            // 文件名
    private String packageName;         // 包名
    private String className;           // 类名
    private String methodName;          // 方法名
    private String surroundingCode;     // 周围的代码（上下文）

    public CodeContext() {
    }

    // Getters and Setters
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

    public String getSurroundingCode() {
        return surroundingCode;
    }

    public void setSurroundingCode(String surroundingCode) {
        this.surroundingCode = surroundingCode;
    }

    public CodeReference getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(CodeReference sourceReference) {
        this.sourceReference = sourceReference;
    }

    /**
     * 格式化代码上下文为可读字符串
     */
    public String formatContext() {
        StringBuilder sb = new StringBuilder();
        if (sourceReference != null) {
            sb.append(sourceReference).append("\n\n");
        }
        if (packageName != null) {
            sb.append("Package: ").append(packageName).append("\n");
        }
        if (fileName != null) {
            sb.append("File: ").append(fileName).append("\n");
        }
        if (className != null) {
            sb.append("Class: ").append(className).append("\n");
        }
        if (methodName != null) {
            sb.append("Method: ").append(methodName).append("\n\n");
        }
        if (surroundingCode != null) {
            sb.append("Surrounding Code:\n```java\n").append(surroundingCode).append("\n```");
        }
        return sb.toString();
    }
}


