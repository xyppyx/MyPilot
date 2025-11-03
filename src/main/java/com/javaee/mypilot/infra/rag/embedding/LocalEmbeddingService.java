package com.javaee.mypilot.infra.rag.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 本地嵌入服务（无需 API，适用于测试和轻量级场景）
 *
 * <p>实现策略：
 * <ul>
 *   <li>基于字符 n-gram 特征提取</li>
 *   <li>TF-IDF 权重计算</li>
 *   <li>结合文本统计特征（长度、词频等）</li>
 *   <li>使用确定性哈希保证相同文本生成相同向量</li>
 * </ul>
 *
 * <p>注意：此实现适合中小规模知识库（< 10000 文档），
 * 对于大规模生产环境建议使用专业的 Embedding 服务（如 DashScope、Zhipu）。
 */
public class LocalEmbeddingService implements EmbeddingService {
    private static final int EMBEDDING_DIM = 384;
    private static final int NGRAM_MIN = 2;
    private static final int NGRAM_MAX = 4;
    private static final int TOP_NGRAMS = 200; // 保留前 N 个最常见的 n-gram

    // 中文和英文停用词
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        // 中文停用词
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这",
        "那", "里", "为", "子", "大", "来", "可以", "对", "生", "能", "而", "还", "与", "地", "中", "被", "或", "等", "但", "及", "之", "所", "以", "个", "用", "他", "她", "它", "们",
        "最", "于", "并", "把", "让", "从", "给", "由", "则", "却", "比", "更", "非常", "太", "十分", "已", "已经", "将", "其", "时", "可", "如", "所以", "因为", "如果", "这样",
        // 英文停用词
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "but", "in", "with", "to", "for", "of", "as", "by", "that", "this", "it", "from", "be", "are", "was", "were", "been",
        "has", "have", "had", "do", "does", "did", "will", "would", "should", "could", "may", "might", "can", "must", "shall"
    ));

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]+");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIM];
        }

        text = text.toLowerCase().trim();

        // 1. 提取 n-gram 特征
        Map<String, Double> ngramFeatures = extractNgramFeatures(text);

        // 2. 提取词频特征
        Map<String, Double> wordFeatures = extractWordFeatures(text);

        // 3. 提取统计特征
        double[] statisticalFeatures = extractStatisticalFeatures(text);

        // 4. 组合特征并投影到固定维度
        float[] embedding = projectToEmbedding(text, ngramFeatures, wordFeatures, statisticalFeatures);

        // 5. L2 归一化
        normalize(embedding);

        return embedding;
    }

    /**
     * 提取 n-gram 特征（字符级别）
     */
    private Map<String, Double> extractNgramFeatures(String text) {
        Map<String, Integer> ngramCounts = new HashMap<>();

        // 常见的中文虚词字符（用于过滤无意义的 n-gram）
        String commonChars = "的了在是我有和就不人都一上也很到说要去你会着看好自己这那里为子大来对生能而还与地中或等但及之所个用他她它们最于把从给由则比更";

        // 提取不同长度的 n-gram
        for (int n = NGRAM_MIN; n <= NGRAM_MAX; n++) {
            for (int i = 0; i <= text.length() - n; i++) {
                String ngram = text.substring(i, i + n);

                // 过滤规则：
                // 1. 必须包含字母或汉字
                // 2. 如果是纯中文 n-gram，不能全是虚词字符
                if (!ngram.matches(".*[\\u4e00-\\u9fa5a-zA-Z0-9].*")) {
                    continue; // 跳过纯标点/空格
                }

                // 检查是否是纯虚词 n-gram
                boolean isAllCommon = true;
                for (char c : ngram.toCharArray()) {
                    if (commonChars.indexOf(c) == -1 && !Character.isWhitespace(c)) {
                        isAllCommon = false;
                        break;
                    }
                }

                if (!isAllCommon) {
                    ngramCounts.merge(ngram, 1, Integer::sum);
                }
            }
        }

        // 转换为频率
        int totalNgrams = ngramCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> ngramFeatures = new HashMap<>();

        if (totalNgrams > 0) {
            ngramCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_NGRAMS)
                .forEach(entry -> ngramFeatures.put(
                    entry.getKey(),
                    (double) entry.getValue() / totalNgrams
                ));
        }

        return ngramFeatures;
    }

    /**
     * 提取词频特征（词级别）
     */
    private Map<String, Double> extractWordFeatures(String text) {
        Map<String, Integer> wordCounts = new HashMap<>();
        var matcher = WORD_PATTERN.matcher(text);

        while (matcher.find()) {
            String word = matcher.group();
            // 过滤停用词和单字符词
            if (!STOP_WORDS.contains(word) && word.length() > 1) {
                wordCounts.merge(word, 1, Integer::sum);
            }
        }

        // 计算 TF（词频）
        int totalWords = wordCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> wordFeatures = new HashMap<>();

        if (totalWords > 0) {
            wordCounts.forEach((word, count) ->
                wordFeatures.put(word, (double) count / totalWords)
            );
        }

        return wordFeatures;
    }

    /**
     * 提取统计特征
     */
    private double[] extractStatisticalFeatures(String text) {
        double[] features = new double[5]; // 减少到5个关键特征

        // 文本长度（归一化）
        features[0] = Math.min(text.length() / 1000.0, 1.0);

        // 中文字符比例
        long chineseCount = CHINESE_PATTERN.matcher(text).results().count();
        features[1] = text.length() > 0 ? (double) chineseCount / text.length() : 0;

        // 数字比例
        long digitCount = text.chars().filter(Character::isDigit).count();
        features[2] = text.length() > 0 ? (double) digitCount / text.length() : 0;

        // 平均词长
        var matcher = WORD_PATTERN.matcher(text);
        List<String> words = new ArrayList<>();
        while (matcher.find()) {
            words.add(matcher.group());
        }
        features[3] = words.isEmpty() ? 0 : words.stream().mapToInt(String::length).average().orElse(0) / 20.0;

        // 词汇多样性（不同词 / 总词数）
        features[4] = words.isEmpty() ? 0 : (double) new HashSet<>(words).size() / words.size();

        return features;
    }

    /**
     * 将特征投影到固定维度的嵌入向量
     */
    private float[] projectToEmbedding(String text, Map<String, Double> ngramFeatures,
                                       Map<String, Double> wordFeatures, double[] statisticalFeatures) {
        float[] embedding = new float[EMBEDDING_DIM];

        // 稳妥的特征权重策略（平衡版本）
        // 目标：在检索准确性和相似度区分度之间取得平衡

        // 1. n-gram 特征（辅助特征，权重适中）
        for (Map.Entry<String, Double> entry : ngramFeatures.entrySet()) {
            int[] indices = getFeatureIndices("ngram_" + entry.getKey(), 3);
            double weight = entry.getValue() * 1.5;
            for (int idx : indices) {
                embedding[idx] += (float) weight;
            }
        }

        // 2. 词特征（主要特征，权重最高）
        // 词汇是最可靠的语义单位，应该占主导地位
        for (Map.Entry<String, Double> entry : wordFeatures.entrySet()) {
            int[] indices = getFeatureIndices("word_" + entry.getKey(), 4);
            double weight = entry.getValue() * 3.5;
            for (int idx : indices) {
                embedding[idx] += (float) weight;
            }
        }

        // 3. 统计特征（微调特征，权重很低）
        // 添加少量统计特征有助于处理边缘情况，但权重必须很低
        for (int i = 0; i < statisticalFeatures.length; i++) {
            int[] indices = getFeatureIndices("stat_" + i, 2);
            double weight = statisticalFeatures[i] * 0.2;
            for (int idx : indices) {
                embedding[idx] += (float) weight;
            }
        }

        // 注意：不再添加随机噪声，完全依靠真实特征
        // 这样可以确保：
        // - 有共同词汇的文本 -> 高相似度
        // - 有共同 n-gram 的文本 -> 中等相似度
        // - 完全不相关的文本 -> 低相似度（接近0）

        return embedding;
    }

    /**
     * 使用确定性哈希将特征映射到多个索引
     * 使用更分散的哈希策略，确保不同特征映射到不同位置
     */
    private int[] getFeatureIndices(String feature, int numIndices) {
        int[] indices = new int[numIndices];
        Set<Integer> usedIndices = new HashSet<>();

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(feature.getBytes(StandardCharsets.UTF_8));

            int attempt = 0;
            for (int i = 0; i < numIndices; i++) {
                int idx;
                do {
                    int offset = (i + attempt) * 4;
                    if (offset + 3 < hash.length) {
                        int value = ((hash[offset % hash.length] & 0xFF) << 24) |
                                   ((hash[(offset + 1) % hash.length] & 0xFF) << 16) |
                                   ((hash[(offset + 2) % hash.length] & 0xFF) << 8) |
                                   (hash[(offset + 3) % hash.length] & 0xFF);
                        idx = Math.abs(value) % EMBEDDING_DIM;
                    } else {
                        idx = Math.abs((feature.hashCode() + i * 31 + attempt * 97)) % EMBEDDING_DIM;
                    }
                    attempt++;
                } while (usedIndices.contains(idx) && attempt < 100); // 避免重复索引

                indices[i] = idx;
                usedIndices.add(idx);
            }
        } catch (NoSuchAlgorithmException e) {
            // 降级为简单哈希
            for (int i = 0; i < numIndices; i++) {
                int idx;
                do {
                    idx = Math.abs((feature.hashCode() + i * 31 + usedIndices.size() * 97)) % EMBEDDING_DIM;
                } while (usedIndices.contains(idx));
                indices[i] = idx;
                usedIndices.add(idx);
            }
        }
        return indices;
    }

    /**
     * 从文本生成确定性种子
     */
    private long generateSeed(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));

            long seed = 0;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            return seed;
        } catch (NoSuchAlgorithmException e) {
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
