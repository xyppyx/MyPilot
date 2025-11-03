package com.javaee.mypilot.infra.chat;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Token 评估器
 */
@Service(Service.Level.PROJECT)
public final class TokenEvaluator {

    private final Project project;

    // 简单的分词模式：按空格、标点符号和数字边界分割
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\s+|[!\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~]|\\d+");

    public TokenEvaluator(Project project) {
        this.project = project;
    }

    /**
     * 估算字符串的token数量
     * @param text 输入字符串
     * @return 估算的token数量
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // 英文和数字的处理
        String[] tokens = TOKEN_PATTERN.split(text);
        int tokenCount = tokens.length;

        // 处理分隔符本身也是token
        int delimiterCount = 0;
        var matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            delimiterCount++;
        }

        // 处理中文和其他Unicode字符
        // 中文字符通常每个字符是一个token
        int unicodeCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                unicodeCount++;
            }
        }

        return tokenCount + delimiterCount + unicodeCount;
    }

    /**
     * 估算字符串列表的总token数量
     * @param texts 字符串列表
     * @return 总token数量
     */
    public int estimateTokensForList(List<String> texts) {
        return texts.stream()
                .mapToInt(this::estimateTokens)
                .sum();
    }

    /**
     * 评估token使用量是否达到阈值
     * @param currentTokens 当前token使用量
     * @return 是否达到阈值
     */
    public boolean isThresholdReached(Integer currentTokens) {
        double usagePercentage = Double.valueOf(currentTokens) / Chat.MAX_TOKENS;
        return usagePercentage >= Chat.COMPRESSION_THRESHOLD;
    }
}