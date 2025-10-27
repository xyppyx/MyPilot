package com.javaee.mypilot.core.model.rag;

/**
 * 引用信息
 */
public class Citation {

    private String source;
    private int pageNumber;
    private String content;
    private float relevanceScore;

    public Citation(String source, int pageNumber, String content, float relevanceScore) {
        this.source = source;
        this.pageNumber = pageNumber;
        this.content = content;
        this.relevanceScore = relevanceScore;
    }

    public Citation(String source, int pageNumber) {
        this(source, pageNumber, "", 0.0f);
    }

    // Getter & Setter
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(float relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}

