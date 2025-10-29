package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;

import java.io.File;
import java.util.List;

/**
 * 文档处理器接口
 */
public interface DocumentProcessor {
    /**
     * 解析文档，分块并生成嵌入
     *
     * @param file 文档文件（PDF, PPT, PPTX 等）
     * @param sourceType 文档来源类型（静态资源或用户上传）
     * @return 文档块列表
     */
    List<DocumentChunk> process(File file, DocumentChunk.SourceType sourceType);
}