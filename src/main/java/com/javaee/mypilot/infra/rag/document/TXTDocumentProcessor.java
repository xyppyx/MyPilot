package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 处理纯文本文件（TXT）的 DocumentProcessor 实现
 */
public class TXTDocumentProcessor implements DocumentProcessor {
    private final EmbeddingService embeddingService;
    private static final int CHUNK_SIZE = 1000; // 每个chunk的字符数

    public TXTDocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<DocumentChunk> process(File file, DocumentChunk.SourceType sourceType) {
        List<DocumentChunk> chunks = new ArrayList<>();

        try {
            // 尝试检测文件编码（先尝试UTF-8，失败则使用GBK）
            String content = readFileWithEncoding(file, StandardCharsets.UTF_8);
            if (content == null || content.contains("�")) {
                // UTF-8失败，尝试GBK
                content = readFileWithEncoding(file, Charset.forName("GBK"));
            }

            if (content == null || content.trim().isEmpty()) {
                System.out.println("文件为空或无法读取: " + file.getName());
                return chunks;
            }

            // 将内容分块
            List<String> textChunks = splitIntoChunks(content, CHUNK_SIZE);

            for (int i = 0; i < textChunks.size(); i++) {
                String chunkContent = textChunks.get(i);
                float[] embedding = embeddingService.embed(chunkContent);

                // 估算页码（假设每1000字符为一页）
                int pageEstimate = i + 1;
                String title = "块" + (i + 1);

                DocumentChunk chunk = new DocumentChunk(
                        UUID.randomUUID().toString(),
                        chunkContent,
                        file.getName(),
                        pageEstimate,
                        title,
                        embedding,
                        sourceType);
                chunks.add(chunk);
            }

        } catch (IOException e) {
            throw new RuntimeException("解析TXT文件失败: " + file.getName(), e);
        }

        return chunks;
    }

    /**
     * 使用指定编码读取文件
     */
    private String readFileWithEncoding(File file, Charset charset) throws IOException {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, charset);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
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

        // 优先按段落分割（双换行符）
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            // 如果单个段落就超过限制，按句子分割
            if (paragraph.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                chunks.addAll(splitLongParagraph(paragraph, chunkSize));
            } else if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(paragraph).append("\n\n");
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 分割过长的段落
     */
    private List<String> splitLongParagraph(String paragraph, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 按句子分割（中英文句号、问号、感叹号）
        String[] sentences = paragraph.split("(?<=[。！？.!?])");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(sentence);
            } else {
                currentChunk.append(sentence);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}

