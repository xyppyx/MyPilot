package com.javaee.mypilot.infra.rag.vector;

import com.javaee.mypilot.core.model.rag.DocumentChunk;

import java.util.List;
public interface VectorDatabase {
    /**
     * 索引知识分块
     */
    void index(List<DocumentChunk> chunks);

    /**
     * 相似度检索
     * 
     * @param embedding 查询向量
     * @param topK      返回数量
     * @return 最相关的分块
     */
    List<DocumentChunk> search(float[] embedding, int topK);
}
