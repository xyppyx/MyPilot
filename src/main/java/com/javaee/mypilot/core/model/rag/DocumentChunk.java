package com.javaee.mypilot.core.model.rag;

/**
 * 文档片段，包含内容、来源、页码、标题、嵌入向量和相似度分数
 */
public class DocumentChunk {

    /**
     * 文档来源类型
     */
    public enum SourceType {
        STATIC,        // 静态资源（插件内置的PPT/PDF）
        USER_UPLOADED  // 用户上传的文档
    }

    private String id;
    private String content;
    private String source; // 文件名或文档名
    private int pageNumber; // 页码或范围
    private String title; // 可选：章节或标题
    private float[] embedding;
    private float similarity; // 相似度分数
    private SourceType sourceType; // 文档来源类型

    public DocumentChunk(String id, String content, String source, int pageNumber, String title, float[] embedding) {
        this.id = id;
        this.content = content;
        this.source = source;
        this.pageNumber = pageNumber;
        this.title = title;
        this.embedding = embedding;
        this.similarity = 0.0f;
        this.sourceType = SourceType.USER_UPLOADED; // 默认为用户上传
    }

    public DocumentChunk(String id, String content, String source, int pageNumber, String title, float[] embedding, SourceType sourceType) {
        this.id = id;
        this.content = content;
        this.source = source;
        this.pageNumber = pageNumber;
        this.title = title;
        this.embedding = embedding;
        this.similarity = 0.0f;
        this.sourceType = sourceType;
    }

    // Getter & Setter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public float getSimilarity() {
        return similarity;
    }

    public void setSimilarity(float similarity) {
        this.similarity = similarity;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }
}
