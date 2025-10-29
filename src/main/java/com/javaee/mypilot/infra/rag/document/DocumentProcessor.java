package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;

import java.io.File;
import java.util.List;

public interface DocumentProcessor {
    /**
     * 解析课程文档，分块并生成嵌入
     *
     * @param file 课程材料文件（如PPT）
     * @return 分块后的知识列表
     */
    List<DocumentChunk> process(File file);


}