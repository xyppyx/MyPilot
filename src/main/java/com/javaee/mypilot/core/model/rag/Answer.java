package com.javaee.mypilot.core.model.rag;

import java.util.List;

/**
 * 回答模型，包含回答内容、引用信息及是否来自课程资料
 */
public class Answer {

    private String content;
    private List<Citation> citations;
    private boolean isFromCourse;

    public Answer(String content, List<Citation> citations, boolean isFromCourse) {
        this.content = content;
        this.citations = citations;
        this.isFromCourse = isFromCourse;
    }

    // Getter & Setter
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }

    public boolean isFromCourse() {
        return isFromCourse;
    }

    public void setFromCourse(boolean fromCourse) {
        isFromCourse = fromCourse;
    }
}