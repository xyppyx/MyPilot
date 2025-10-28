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
        
        **你的回复必须严格遵循以下 JSON 格式。**
        **你需要返回一个或多个 'actions' 对象，每个对象都精确描述一个代码变更。**
        
        **关键规则：**
        1.  如果修改现有代码，'type' 必须是 "REPLACE"，并提供 'startLine' 和 'endLine' 来**精确界定旧代码的范围**。
        2.  'filePath' 必须与你提供的代码上下文中的文件路径完全匹配。
        3.  'oldCode' 字段请**包含你正在替换的旧代码文本**，这对于用户验证至关重要。
        
        请使用以下JSON格式进行回复：
        {
          "explanation": "<你的中文解释>",
          "actions": [
            {
              "type": "REPLACE" | "INSERT" | "DELETE",
              "filePath": "<文件路径/URL，例如：file:///project/src/MyClass.java>",
              "startLine": <起始行号>,
              "endLine": <结束行号>,
              "oldCode": "<要替换/删除的旧代码文本，多行请使用 \n 分隔>",
              "newCode": "<新生成的代码文本，仅用于 REPLACE 或 INSERT，多行请使用 \n 分隔>"
            }
          ]
        }
        
        例如 (REPLACE)：
        {
          "explanation": "为了处理并发情况，我将原来的 int 字段替换为了 AtomicInteger。",
          "actions": [
            {
              "type": "REPLACE",
              "filePath": "{fileNameOfReference}",
              "startLine": 5,
              "endLine": 5,
              "oldCode": "private int counter = 0;",
              "newCode": "private final AtomicInteger counter = new AtomicInteger(0);"
            }
          ]
        }
        
        代码上下文 (CODE CONTEXT){
        
        {codeContext}
        
        }
        
        会话历史 (SESSION HISTORY){
        
        {sessionContext}
        
        }
        
        用户指令 (USER MESSAGE){
        
        {userMessage}
        
        }
        """;

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