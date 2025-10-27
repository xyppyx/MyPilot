package com.javaee.mypilot.infra.api;

import com.intellij.openapi.components.Service;

/**
 * agent prompt管理类，负责构建和管理与智能代理交互的提示信息。
 * prompt组成: 系统/角色指令+代码上下文+聊天上下文+用户Message
 */
public class AgentPrompt {

    /**
     * 固定Prompt模板，定义了各个组成部分的结构和分隔符。
     * 使用占位符 {codeContext}, {sessionContext}, {userMessage}。
     * 同时也包含了固定的系统/角色指令。
     */
    private static final String PROMPT_TEMPLATE = """
        你是一位经验丰富的Java开发者，专注于IntelliJ IDEA环境下的代码辅助工具开发。\
        你的任务是分析提供的代码上下文，对话上下文和用户请求，然后根据要求生成或修改代码。\
        请始终确保代码的准确性和符合当前语言的习惯用法。 \
        你的回复必须严格分为两部分：首先是中文的**语言解释**，其次是**代码**。 \
        请使用以下JSON格式进行回复： \
        {
          "explanation": "<你的中文解释>",
          "code": "<你的代码>"
        }
        例如：\
        {
          "explanation": "为了提高性能，我将您的方法重构为使用 Java 8 Stream API。新的实现简洁地完成了过滤和收集操作。",
          "code": "public List<User> filter(List<User> users) {\\n    return users.stream()\\n        .filter(u -> u.getStatus() == Status.ACTIVE)\\n        .collect(Collectors.toList());\\n}"
        }
        
        --- 代码上下文 (CODE CONTEXT) ---
        {codeContext}
        ---------------------------------
        
        --- 会话历史 (SESSION HISTORY) ---
        {sessionContext}
        -----------------------------------
        
        --- 用户指令 (USER MESSAGE) ---
        {userMessage}
        -------------------------------""";

    /**
     * 构建完整的提示信息 (Prompt)。
     * @param codeContext 当前文件内容、选中代码等代码相关信息。
     * @param sessionContext 之前的聊天历史记录。
     * @param userMessage 用户本次输入的具体指令。
     * @return 拼接好的完整Prompt字符串。
     */
    public static String buildPrompt(String codeContext, String sessionContext, String userMessage) {
        // 对输入进行空值处理，确保Prompt结构完整
        if (codeContext == null) {
            codeContext = "未提供具体的代码上下文。";
        }
        if (sessionContext == null || sessionContext.trim().isEmpty()) {
            sessionContext = "无历史会话记录。";
        }
        if (userMessage == null) {
            userMessage = "用户未提供具体指令。";
        }

        // 使用 String.replace 方法替换模板中的占位符
        return PROMPT_TEMPLATE
                .replace("{codeContext}", codeContext)
                .replace("{sessionContext}", sessionContext)
                .replace("{userMessage}", userMessage);
    }
}