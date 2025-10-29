package com.javaee.mypilot.infra.rag.document;

import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 处理 Markdown 文件（MD）的 DocumentProcessor 实现
 * 按照 Markdown 的层级结构（标题）进行智能分块
 */
public class MarkdownDocumentProcessor implements DocumentProcessor {
    private final EmbeddingService embeddingService;
    private static final int CHUNK_SIZE = 1000; // 每个chunk的字符数
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    public MarkdownDocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public List<DocumentChunk> process(File file, DocumentChunk.SourceType sourceType) {
        List<DocumentChunk> chunks = new ArrayList<>();

        try {
            String content = readFile(file);

            if (content == null || content.trim().isEmpty()) {
                System.out.println("Markdown文件为空: " + file.getName());
                return chunks;
            }

            // 按照Markdown结构分块
            List<MarkdownSection> sections = parseMarkdownSections(content);

            int chunkIndex = 1;
            for (MarkdownSection section : sections) {
                // 如果section内容过长，进一步分块
                if (section.content.length() > CHUNK_SIZE) {
                    List<String> subChunks = splitIntoChunks(section.content, CHUNK_SIZE);
                    for (int i = 0; i < subChunks.size(); i++) {
                        saveChunk(chunks, subChunks.get(i), file.getName(),
                                 section.title, chunkIndex++, sourceType);
                    }
                } else {
                    saveChunk(chunks, section.content, file.getName(),
                             section.title, chunkIndex++, sourceType);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("解析Markdown文件失败: " + file.getName(), e);
        }

        return chunks;
    }

    /**
     * 读取文件内容
     */
    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        return content.toString();
    }

    /**
     * 解析Markdown文档，按标题分段
     */
    private List<MarkdownSection> parseMarkdownSections(String content) {
        List<MarkdownSection> sections = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "开始";

        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line);

            if (matcher.matches()) {
                // 遇到新标题，保存之前的section
                if (currentContent.length() > 0) {
                    sections.add(new MarkdownSection(currentTitle, currentContent.toString().trim()));
                    currentContent = new StringBuilder();
                }

                // 开始新section
                String level = matcher.group(1); // # 的数量
                currentTitle = matcher.group(2).trim(); // 标题文本
                currentContent.append(line).append("\n");
            } else {
                currentContent.append(line).append("\n");
            }
        }

        // 保存最后一个section
        if (currentContent.length() > 0) {
            sections.add(new MarkdownSection(currentTitle, currentContent.toString().trim()));
        }

        return sections;
    }

    /**
     * 保存文档块
     */
    private void saveChunk(List<DocumentChunk> chunks, String content, String fileName,
                          String sectionTitle, int chunkIndex, DocumentChunk.SourceType sourceType) {
        float[] embedding = embeddingService.embed(content);

        String title = sectionTitle + " - 块" + chunkIndex;

        DocumentChunk chunk = new DocumentChunk(
                UUID.randomUUID().toString(),
                content.trim(),
                fileName,
                chunkIndex, // 使用块索引作为"页码"
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

    /**
     * Markdown 章节内部类
     */
    private static class MarkdownSection {
        final String title;
        final String content;

        MarkdownSection(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}


