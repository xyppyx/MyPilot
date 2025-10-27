package com.javaee.mypilot.infra.rag.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * 本地模拟嵌入服务（用于测试，无需 API）
 * 注意：这个实现仅用于开发测试，不适合生产环境
 * 使用确定性哈希生成向量，相同文本生成相同向量
 */
public class LocalEmbeddingService implements EmbeddingService {
    private static final int EMBEDDING_DIM = 384; // 常见的嵌入维度

    @Override
    public float[] embed(String text) {
        // 使用 SHA-256 生成确定性种子
        long seed = generateSeed(text);
        Random random = new Random(seed);

        float[] embedding = new float[EMBEDDING_DIM];

        // 生成高斯分布的随机向量
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding[i] = (float) random.nextGaussian();
        }

        // L2 归一化
        normalize(embedding);

        return embedding;
    }

    /**
     * 从文本生成确定性种子
     */
    private long generateSeed(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));

            // 使用前 8 个字节作为 long 种子
            long seed = 0;
            for (int i = 0; i < 8; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
            // 降级为简单哈希
            return text.hashCode();
        }
    }

    /**
     * L2 归一化向量
     */
    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
