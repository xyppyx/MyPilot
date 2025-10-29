package com.javaee.mypilot.infra.api;

import com.javaee.mypilot.core.model.rag.document.DocumentChunk;

import java.util.List;

public class RagPrompt {
    /**
     * 构建带知识库上下文的 Prompt
     */
    public String buildPromptWithContext(String question, List<DocumentChunk> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个Java课程的教学助手。请根据以下课程材料回答学生的问题。\n\n");
        prompt.append("课程材料：\n");
        prompt.append("=".repeat(50)).append("\n");

        for (int i = 0; i < context.size(); i++) {
            DocumentChunk chunk = context.get(i);
            prompt.append(String.format("[参考资料 %d] 来源：%s (第%d页)\n",
                    i + 1, chunk.getSource(), chunk.getPageNumber()));
            prompt.append(chunk.getContent()).append("\n\n");
        }

        prompt.append("=".repeat(50)).append("\n\n");
        prompt.append("学生问题：").append(question).append("\n\n");
        prompt.append("请基于以上课程材料回答问题。如果课程材料中没有相关信息，请说明并给出你的理解。");

        return prompt.toString();
    }

    /**
     * 构建通用 Prompt（无知识库上下文）
     */
    public String buildGeneralPrompt(String question) {
        return "你是一个Java课程的教学助手。学生问题：" + question +
                "\n\n注意：知识库中没有找到相关的课程材料，请基于你的通用知识回答这个问题。";
    }

    /**
     * 构建带代码上下文的 Prompt
     */
    public String buildPromptWithCodeContext(String question, String codeContext, List<DocumentChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个Java课程的教学助手。请根据以下课程材料和代码上下文回答学生的问题。\n\n");

        // 添加代码上下文
        prompt.append("代码上下文：\n");
        prompt.append("```java\n");
        prompt.append(codeContext).append("\n");
        prompt.append("```\n\n");

        // 添加课程材料
        if (!chunks.isEmpty()) {
            prompt.append("课程材料：\n");
            prompt.append("=".repeat(50)).append("\n");
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                prompt.append(String.format("[参考资料 %d] 来源：%s (第%d页)\n",
                        i + 1, chunk.getSource(), chunk.getPageNumber()));
                prompt.append(chunk.getContent()).append("\n\n");
            }
            prompt.append("=".repeat(50)).append("\n\n");
        }

        prompt.append("学生问题：").append(question).append("\n\n");
        prompt.append("请结合代码上下文和课程材料回答问题。");

        return prompt.toString();
    }

    /**
     * 构建带代码上下文但无知识库的 Prompt
     */
    public String buildPromptWithCodeContextOnly(String question, String codeContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个Java课程的教学助手。\n\n");
        prompt.append("代码上下文：\n");
        prompt.append("```java\n");
        prompt.append(codeContext).append("\n");
        prompt.append("```\n\n");
        prompt.append("学生问题：").append(question).append("\n\n");
        prompt.append("注意：知识库中没有找到相关的课程材料，请结合代码上下文和你的通用知识回答这个问题。");

        return prompt.toString();
    }
}
