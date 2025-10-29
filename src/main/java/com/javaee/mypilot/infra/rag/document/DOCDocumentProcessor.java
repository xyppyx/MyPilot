package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 使用 Apache POI 解析 DOC/DOCX 文件的 DocumentProcessor 实现
 */
public class DOCDocumentProcessor implements DocumentProcessor {
    private final EmbeddingService embeddingService;
    private static final int CHUNK_SIZE = 1000; // 每个chunk的字符数

    public DOCDocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<DocumentChunk> process(File file, DocumentChunk.SourceType sourceType) {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".docx")) {
            return processDocx(file, sourceType);
        } else if (fileName.endsWith(".doc")) {
            return processDoc(file, sourceType);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + fileName);
        }
    }

    /**
     * 处理 DOCX 文件 (Office 2007+)
     */
    private List<DocumentChunk> processDocx(File file, DocumentChunk.SourceType sourceType) {
        List<DocumentChunk> chunks = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            StringBuilder currentChunk = new StringBuilder();
            int chunkIndex = 1;
            int pageEstimate = 1; // DOCX没有明确的页码概念，使用估算

            for (XWPFParagraph para : paragraphs) {
                String text = para.getText().trim();

                if (text.isEmpty()) {
                    continue;
                }

                // 如果当前块加上新段落超过限制，先保存当前块
                if (currentChunk.length() + text.length() > CHUNK_SIZE && currentChunk.length() > 0) {
                    saveChunk(chunks, currentChunk.toString(), file.getName(),
                            pageEstimate, chunkIndex, sourceType);
                    currentChunk = new StringBuilder();
                    chunkIndex++;
                    pageEstimate = chunkIndex; // 简单估算页码
                }

                currentChunk.append(text).append("\n");
            }

            // 保存最后一个块
            if (currentChunk.length() > 0) {
                saveChunk(chunks, currentChunk.toString(), file.getName(),
                        pageEstimate, chunkIndex, sourceType);
            }

        } catch (IOException e) {
            throw new RuntimeException("解析DOCX文件失败: " + file.getName(), e);
        }

        return chunks;
    }

    /**
     * 处理 DOC 文件 (Office 97-2003)
     */
    private List<DocumentChunk> processDoc(File file, DocumentChunk.SourceType sourceType) {
        List<DocumentChunk> chunks = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis)) {

            WordExtractor extractor = new WordExtractor(document);
            String fullText = extractor.getText();

            // 将全文分块
            List<String> textChunks = splitIntoChunks(fullText, CHUNK_SIZE);

            for (int i = 0; i < textChunks.size(); i++) {
                String chunkContent = textChunks.get(i);
                int pageEstimate = i + 1;
                saveChunk(chunks, chunkContent, file.getName(),
                        pageEstimate, i + 1, sourceType);
            }

            extractor.close();

        } catch (IOException e) {
            throw new RuntimeException("解析DOC文件失败: " + file.getName(), e);
        }

        return chunks;
    }

    /**
     * 保存文档块
     */
    private void saveChunk(List<DocumentChunk> chunks, String content, String fileName,
                           int pageNumber, int chunkIndex, DocumentChunk.SourceType sourceType) {
        float[] embedding = embeddingService.embed(content);

        String title = "块" + chunkIndex + " (约第" + pageNumber + "页)";

        DocumentChunk chunk = new DocumentChunk(
                UUID.randomUUID().toString(),
                content.trim(),
                fileName,
                pageNumber,
                title,
                embedding,
                sourceType);
        chunks.add(chunk);
    }

    /**
     * 将长文本分割成多个块
     */
    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // 按段落分割
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            currentChunk.append(paragraph).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}