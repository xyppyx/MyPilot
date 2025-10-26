package com.javaee.mypilot.core.consts;

/**
 * 聊天相关常量
 */
public class Chat {
    // 聊天角色
    public static final String SYSTEM_ROLE = "system";

    // 用户角色
    public static final String USER_ROLE = "user";

    // 助手角色
    public static final String ASSISTANT_ROLE = "assistant";

    // 默认对话标题
    public static final String DEFAULT_TITLE = "新对话";

    // 消息令牌限制(8192 tokens)
    public static final Integer MAX_TOKENS = 8192;

    // 消息压缩阈值
    public static final Double COMPRESSION_THRESHOLD = 0.90;

    // 为新消息保留的最小空间
    public static final Integer MIN_TOKENS_RESERVE = 500;
}
