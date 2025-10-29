package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 使用 Apache POI 解析 PPTX 文件的 DocumentProcessor 实现
 */
public class PPTDocumentProcessor implements DocumentProcessor {
    private final EmbeddingService embeddingService;

    public PPTDocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<DocumentChunk> process(File file, DocumentChunk.SourceType sourceType) {
        List<DocumentChunk> chunks = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                StringBuilder text = new StringBuilder();
                String title = slide.getTitle();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        text.append(textShape.getText()).append("\n");
                    }
                }
                String content = text.toString().trim();
                if (!content.isEmpty()) {
                    float[] embedding = embeddingService.embed(content);
                    DocumentChunk chunk = new DocumentChunk(
                            UUID.randomUUID().toString(),
                            content,
                            file.getName(),
                            i + 1, // 页码从1开始
                            title,
                            embedding,
                            sourceType);
                    chunks.add(chunk);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解析PPT文件失败: " + file.getName(), e);
        }
        return chunks;
    }
}



