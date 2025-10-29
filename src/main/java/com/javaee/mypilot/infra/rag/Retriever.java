package com.javaee.mypilot.infra.rag;

import com.javaee.mypilot.core.model.rag.document.DocumentChunk;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import com.javaee.mypilot.infra.rag.vector.VectorDatabase;

import java.util.List;

public class Retriever {
    private final EmbeddingService embeddingService;
    private final VectorDatabase vectorDatabase;

    public Retriever(EmbeddingService embeddingService, VectorDatabase vectorDatabase) {
        this.embeddingService = embeddingService;
        this.vectorDatabase = vectorDatabase;
    }

    /**
     * 检索相关知识分块
     * 
     * @param query 用户问题
     * @param topK  返回数量
     * @return 相关分块（已设置相似度分数）
     */
    public List<DocumentChunk> retrieve(String query, int topK) {
        float[] queryEmbedding = embeddingService.embed(query);

        // 相似度分数已经在 LuceneVectorDatabase.search() 中设置
        return vectorDatabase.search(queryEmbedding, topK);
    }
}
