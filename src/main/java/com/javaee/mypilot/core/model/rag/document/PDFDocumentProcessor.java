package com.javaee.mypilot.core.model.rag.document;

import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 使用 Apache PDFBox 解析 PDF 文件的 DocumentProcessor 实现
 */
public class PDFDocumentProcessor implements DocumentProcessor {
    private final EmbeddingService embeddingService;
    private static final int CHUNK_SIZE = 1000; // 每个chunk的字符数

    public PDFDocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<DocumentChunk> process(File file) {
        List<DocumentChunk> chunks = new ArrayList<>();

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            // 按页处理PDF
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    // 如果页面内容较长，可以进一步分块
                    List<String> pageChunks = splitIntoChunks(pageText, CHUNK_SIZE);

                    for (int i = 0; i < pageChunks.size(); i++) {
                        String chunkContent = pageChunks.get(i);
                        float[] embedding = embeddingService.embed(chunkContent);

                        // 生成标题：如果是单块则用页码，多块则加上块序号
                        String title = pageChunks.size() == 1
                            ? "第" + pageNum + "页"
                            : "第" + pageNum + "页-块" + (i + 1);

                        DocumentChunk chunk = new DocumentChunk(
                                UUID.randomUUID().toString(),
                                chunkContent,
                                file.getName(),
                                pageNum,
                                title,
                                embedding);
                        chunks.add(chunk);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解析PDF文件失败: " + file.getName(), e);
        }

        return chunks;
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

