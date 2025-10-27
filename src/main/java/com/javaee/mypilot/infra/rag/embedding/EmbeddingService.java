package com.javaee.mypilot.infra.rag.embedding;

public interface EmbeddingService {
    /**
     * 生成文本的嵌入向量
     * 
     * @param text 输入文本
     * @return 向量表示
     */
    float[] embed(String text);
}
