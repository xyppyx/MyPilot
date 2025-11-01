package com.javaee.mypilot.infra.rag.vector;

import com.javaee.mypilot.core.model.rag.DocumentChunk;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Lucene 的向量数据库实现
 * 支持向量索引和余弦相似度检索
 */
public class LuceneVectorDatabase implements VectorDatabase {
    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_PAGE = "pageNumber";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_SOURCE_TYPE = "sourceType"; // STATIC or USER_UPLOADED

    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private IndexWriter indexWriter;
    private DirectoryReader indexReader;

    /**
     * 构造函数
     * 
     * @param indexPath 索引存储路径
     */
    public LuceneVectorDatabase(String indexPath) {
        try {
            // Use NIOFSDirectory instead of FSDirectory.open() to avoid MMapDirectory
            // classloader issues with IntelliJ Platform tests
            this.directory = new NIOFSDirectory(Paths.get(indexPath));

            this.analyzer = new StandardAnalyzer();

            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            this.indexWriter = new IndexWriter(directory, config);
        } catch (IOException e) {
            throw new RuntimeException("初始化 Lucene 索引失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void index(List<DocumentChunk> chunks) {
        try {
            for (DocumentChunk chunk : chunks) {
                Document doc = new Document();

                // 存储基本字段
                doc.add(new StringField(FIELD_ID, chunk.getId(), Field.Store.YES));
                doc.add(new TextField(FIELD_CONTENT, chunk.getContent(), Field.Store.YES));
                doc.add(new StringField(FIELD_SOURCE, chunk.getSource(), Field.Store.YES));
                doc.add(new IntPoint(FIELD_PAGE, chunk.getPageNumber()));
                doc.add(new StoredField(FIELD_PAGE, chunk.getPageNumber()));

                if (chunk.getTitle() != null && !chunk.getTitle().isEmpty()) {
                    doc.add(new TextField(FIELD_TITLE, chunk.getTitle(), Field.Store.YES));
                }

                // 存储文档来源类型
                String sourceTypeStr = chunk.getSourceType() != null ?
                    chunk.getSourceType().name() : DocumentChunk.SourceType.USER_UPLOADED.name();
                doc.add(new StringField(FIELD_SOURCE_TYPE, sourceTypeStr, Field.Store.YES));

                // 存储嵌入向量（序列化为字节）
                if (chunk.getEmbedding() != null) {
                    byte[] embeddingBytes = floatArrayToByteArray(chunk.getEmbedding());
                    doc.add(new StoredField(FIELD_EMBEDDING, embeddingBytes));
                }

                indexWriter.addDocument(doc);
            }

            indexWriter.commit();
            refreshReader();

        } catch (IOException e) {
            throw new RuntimeException("索引文档分块失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        try {
            if (indexReader == null) {
                refreshReader();
            }

            if (indexReader.numDocs() == 0) {
                return Collections.emptyList();
            }

            // 收集所有文档并计算相似度
            List<ScoredDocument> scoredDocs = new ArrayList<>();

            for (int i = 0; i < indexReader.maxDoc(); i++) {
                try {
                    Document doc = indexReader.storedFields().document(i);
                    BytesRef embeddingBytes = doc.getBinaryValue(FIELD_EMBEDDING);

                    if (embeddingBytes != null) {
                        float[] docEmbedding = byteArrayToFloatArray(embeddingBytes.bytes);
                        float similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                        scoredDocs.add(new ScoredDocument(doc, similarity));
                    }
                } catch (Exception e) {
                    // 跳过损坏的文档
                    continue;
                }
            }

            // 按相似度降序排序
            scoredDocs.sort((a, b) -> Float.compare(b.score, a.score));

            // 取 topK 个结果
            List<DocumentChunk> results = new ArrayList<>();
            int limit = Math.min(topK, scoredDocs.size());

            for (int i = 0; i < limit; i++) {
                ScoredDocument scoredDoc = scoredDocs.get(i);
                Document doc = scoredDoc.document;

                BytesRef embeddingBytes = doc.getBinaryValue(FIELD_EMBEDDING);
                float[] embedding = byteArrayToFloatArray(embeddingBytes.bytes);

                // 读取 sourceType
                String sourceTypeStr = doc.get(FIELD_SOURCE_TYPE);
                DocumentChunk.SourceType sourceType = sourceTypeStr != null ?
                    DocumentChunk.SourceType.valueOf(sourceTypeStr) :
                    DocumentChunk.SourceType.USER_UPLOADED;

                DocumentChunk chunk = new DocumentChunk(
                        doc.get(FIELD_ID),
                        doc.get(FIELD_CONTENT),
                        doc.get(FIELD_SOURCE),
                        doc.getField(FIELD_PAGE).numericValue().intValue(),
                        doc.get(FIELD_TITLE),
                        embedding,
                        sourceType);

                // 设置相似度分数
                chunk.setSimilarity(scoredDoc.score);

                results.add(chunk);
            }

            return results;

        } catch (IOException e) {
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算余弦相似度
     */
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }

        return (float) (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }

    /**
     * float[] 转换为 byte[]
     */
    private byte[] floatArrayToByteArray(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int intBits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (intBits >> 24);
            bytes[i * 4 + 1] = (byte) (intBits >> 16);
            bytes[i * 4 + 2] = (byte) (intBits >> 8);
            bytes[i * 4 + 3] = (byte) intBits;
        }
        return bytes;
    }

    /**
     * byte[] 转换为 float[]
     */
    private float[] byteArrayToFloatArray(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int intBits = ((bytes[i * 4] & 0xFF) << 24) |
                    ((bytes[i * 4 + 1] & 0xFF) << 16) |
                    ((bytes[i * 4 + 2] & 0xFF) << 8) |
                    (bytes[i * 4 + 3] & 0xFF);
            floats[i] = Float.intBitsToFloat(intBits);
        }
        return floats;
    }

    /**
     * 刷新 IndexReader
     */
    private void refreshReader() throws IOException {
        if (indexReader != null) {
            DirectoryReader newReader = DirectoryReader.openIfChanged(indexReader);
            if (newReader != null) {
                indexReader.close();
                indexReader = newReader;
            }
        } else {
            if (DirectoryReader.indexExists(directory)) {
                indexReader = DirectoryReader.open(directory);
            } else {
                // 如果索引不存在，先提交一次以创建索引
                indexWriter.commit();
                indexReader = DirectoryReader.open(directory);
            }
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        try {
            if (indexWriter != null) {
                indexWriter.close();
            }
            if (indexReader != null) {
                indexReader.close();
            }
            if (directory != null) {
                directory.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("关闭 Lucene 资源失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空索引（删除所有文档）
     */
    public void clear() {
        clear(null);
    }

    /**
     * 根据来源类型清空索引
     * @param sourceType 文档来源类型（null 表示删除所有文档）
     */
    public void clear(DocumentChunk.SourceType sourceType) {
        try {
            if (sourceType == null) {
                // 删除所有文档
                indexWriter.deleteAll();
                System.out.println("已删除所有文档");
            } else {
                // 根据来源类型删除文档
                if (indexReader == null) {
                    refreshReader();
                }

                int deletedCount = 0;
                List<Integer> docIdsToDelete = new ArrayList<>();

                // 收集需要删除的文档ID
                for (int i = 0; i < indexReader.maxDoc(); i++) {
                    try {
                        Document doc = indexReader.storedFields().document(i);
                        String docSourceType = doc.get(FIELD_SOURCE_TYPE);

                        if (docSourceType != null && docSourceType.equals(sourceType.name())) {
                            docIdsToDelete.add(i);
                        }
                    } catch (Exception e) {
                        // 跳过损坏的文档
                        continue;
                    }
                }

                // 使用 Term 删除文档
                //无法通过 docId 直接删除，因为 docId 会在索引合并后变化
                for (int docId : docIdsToDelete) {
                    try {
                        Document doc = indexReader.storedFields().document(docId);
                        String id = doc.get(FIELD_ID);
                        if (id != null) {
                            indexWriter.deleteDocuments(new org.apache.lucene.index.Term(FIELD_ID, id));
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("删除文档失败: " + e.getMessage());
                    }
                }

                System.out.println("已删除 " + deletedCount + " 个 " +
                    (sourceType == DocumentChunk.SourceType.STATIC ? "静态资源" : "用户上传") + " 文档");
            }

            indexWriter.commit();
            refreshReader();
        } catch (IOException e) {
            throw new RuntimeException("清空索引失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取索引中的文档数量
     */
    public int getDocumentCount() {
        try {
            if (indexReader == null) {
                refreshReader();
            }
            return indexReader.numDocs();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 获取知识库中的所有唯一文件列表
     *
     * @return 文件信息列表（包含文件名、来源类型、文档块数量）
     */
    public List<FileInfo> getAllFiles() {
        try {
            if (indexReader == null) {
                refreshReader();
            }

            if (indexReader.numDocs() == 0) {
                return Collections.emptyList();
            }

            // 使用 Map 来统计每个文件的信息
            Map<String, FileInfo> fileMap = new HashMap<>();

            // 遍历所有文档
            for (int i = 0; i < indexReader.maxDoc(); i++) {
                try {
                    Document doc = indexReader.storedFields().document(i);
                    String source = doc.get(FIELD_SOURCE);
                    String sourceTypeStr = doc.get(FIELD_SOURCE_TYPE);

                    if (source == null || source.isEmpty()) {
                        continue;
                    }

                    // 解析来源类型
                    DocumentChunk.SourceType sourceType = DocumentChunk.SourceType.USER_UPLOADED;
                    if (sourceTypeStr != null) {
                        try {
                            sourceType = DocumentChunk.SourceType.valueOf(sourceTypeStr);
                        } catch (IllegalArgumentException e) {
                            // 如果无法解析，使用默认值
                            sourceType = DocumentChunk.SourceType.USER_UPLOADED;
                        }
                    }

                    // 更新或创建文件信息
                    FileInfo fileInfo = fileMap.get(source);
                    if (fileInfo == null) {
                        fileInfo = new FileInfo(source, sourceType, 0);
                        fileMap.put(source, fileInfo);
                    }
                    fileInfo.chunkCount++;
                } catch (Exception e) {
                    // 跳过损坏的文档
                    continue;
                }
            }

            // 转换为列表并返回
            return new ArrayList<>(fileMap.values());
        } catch (IOException e) {
            System.err.println("获取文件列表失败: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 删除指定源文件的所有文档块
     *
     * @param source 源文件名
     * @return 删除的文档数量
     */
    public int deleteBySource(String source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }

        try {
            if (indexReader == null) {
                refreshReader();
            }

            int deletedCount = 0;

            // 收集需要删除的文档ID
            List<String> docIdsToDelete = new ArrayList<>();

            // 遍历所有文档，找到匹配的源文件
            for (int i = 0; i < indexReader.maxDoc(); i++) {
                try {
                    Document doc = indexReader.storedFields().document(i);
                    String docSource = doc.get(FIELD_SOURCE);

                    if (docSource != null && docSource.equals(source)) {
                        String id = doc.get(FIELD_ID);
                        if (id != null) {
                            docIdsToDelete.add(id);
                        }
                    }
                } catch (Exception e) {
                    // 跳过损坏的文档
                    continue;
                }
            }

            // 使用 Term 删除文档
            for (String docId : docIdsToDelete) {
                try {
                    indexWriter.deleteDocuments(new org.apache.lucene.index.Term(FIELD_ID, docId));
                    deletedCount++;
                } catch (Exception e) {
                    System.err.println("删除文档失败: " + e.getMessage());
                }
            }

            // 提交更改并刷新 reader
            if (deletedCount > 0) {
                indexWriter.commit();
                refreshReader();
                System.out.println("已删除文件 " + source + " 的 " + deletedCount + " 个文档块");
            }

            return deletedCount;
        } catch (IOException e) {
            System.err.println("删除文件失败: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 文件信息类
     */
    public static class FileInfo {
        public final String fileName;
        public final DocumentChunk.SourceType sourceType;
        public int chunkCount;

        public FileInfo(String fileName, DocumentChunk.SourceType sourceType, int chunkCount) {
            this.fileName = fileName;
            this.sourceType = sourceType;
            this.chunkCount = chunkCount;
        }

        public String getSourceTypeDisplayName() {
            return sourceType == DocumentChunk.SourceType.STATIC ? "静态资源" : "用户上传";
        }
    }

    /**
     * 内部类：带分数的文档
     */
    private static class ScoredDocument {
        final Document document;
        final float score;

        ScoredDocument(Document document, float score) {
            this.document = document;
            this.score = score;
        }
    }
}
