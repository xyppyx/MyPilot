package com.javaee.mypilot.core.model.rag;

/**
 * 引用信息
 */
public class Citation {

    private String source;
    private int pageNumber;

    public Citation(String source, int pageNumber) {
        this.source = source;
        this.pageNumber = pageNumber;
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
}

