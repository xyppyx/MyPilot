package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.rag.Answer;
import com.javaee.mypilot.core.model.rag.Citation;
import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.api.LlmClient;
import com.javaee.mypilot.infra.api.RagPrompt;
import com.javaee.mypilot.infra.rag.DocumentProcessor;
import com.javaee.mypilot.infra.rag.PDFDocumentProcessor;
import com.javaee.mypilot.infra.rag.PPTDocumentProcessor;
import com.javaee.mypilot.infra.rag.Retriever;
import com.javaee.mypilot.infra.rag.embedding.DashScopeEmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.LocalEmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.ZhipuEmbeddingService;
import com.javaee.mypilot.infra.rag.vector.LuceneVectorDatabase;
import com.javaee.mypilot.infra.rag.vector.VectorDatabase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) 服务
 * RAG Service for knowledge retrieval and answer generation
 */
@Service(Service.Level.PROJECT)
public final class RagService {

    private final Project project;
    private final ConfigService configService;

    // RAG 组件
    private EmbeddingService embeddingService;
    private VectorDatabase vectorDatabase;
    private Retriever retriever;
    private DocumentProcessor pptDocumentProcessor;
    private DocumentProcessor pdfDocumentProcessor;
    private RagPrompt ragPrompt;
    private LlmClient llmClient;

    private boolean initialized = false;

    public RagService(@NotNull Project project) {
        this.project = project;
        this.configService = ConfigService.getInstance(project);
        System.out.println("RagService initialized - call initialize() to start RAG components");
    }

    /**
     * 初始化 RAG 系统
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        try {
            // 1. 初始化 Embedding 服务
            this.embeddingService = createEmbeddingService();

            // 2. 初始化向量数据库
            String indexPath = configService.getKnowledgeBasePath();
            if (indexPath == null || indexPath.isEmpty()) {
                indexPath = System.getProperty("user.home") + File.separator + ".mypilot" + File.separator + "vector_index";
            }
            this.vectorDatabase = new LuceneVectorDatabase(indexPath);

            // 3. 初始化检索器
            this.retriever = new Retriever(embeddingService, vectorDatabase);

            // 4. 初始化文档处理器
            this.pptDocumentProcessor = new PPTDocumentProcessor(embeddingService);
            this.pdfDocumentProcessor = new PDFDocumentProcessor(embeddingService);

            // 5. 初始化 RagPrompt
            this.ragPrompt = new RagPrompt();

            // 6. 初始化 LLM 客户端
            this.llmClient = project.getService(LlmClient.class);

            this.initialized = true;
            System.out.println("RagService components initialized successfully");

            // 7. 自动从PPT文件夹加载知识库（如果知识库为空）
            autoLoadKnowledgeBase();
        } catch (Exception e) {
            System.err.println("Failed to initialize RAG components: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 自动从内置PPT文件夹加载知识库
     */
    private void autoLoadKnowledgeBase() {
        try {
            // 检查知识库是否已有内容
            if (isKnowledgeBaseInitialized()) {
                System.out.println("知识库已存在，跳过自动加载");
                return;
            }

            // 获取PPT文件夹路径
            File pptFolder = getPPTFolder();
            if (pptFolder == null || !pptFolder.exists() || !pptFolder.isDirectory()) {
                System.out.println("PPT文件夹不存在，跳过自动加载: " + (pptFolder != null ? pptFolder.getPath() : "null"));
                return;
            }

            // 收集所有PDF文件
            List<File> pptFiles = new ArrayList<>();
            File[] files = pptFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".pdf") || fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
                        pptFiles.add(file);
                    }
                }
            }

            if (pptFiles.isEmpty()) {
                System.out.println("PPT文件夹中没有找到PDF或PPT文件");
                return;
            }

            System.out.println("发现 " + pptFiles.size() + " 个课程材料文件，开始自动索引...");
            boolean success = initializeKnowledgeBase(pptFiles);
            if (success) {
                System.out.println("知识库自动加载完成！");
            } else {
                System.out.println("知识库自动加载失败");
            }
        } catch (Exception e) {
            System.err.println("自动加载知识库时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取PPT文件夹路径（从配置或默认路径）
     */
    private File getPPTFolder() {
        try {
            // 1. 首先尝试从配置中获取路径
            String configuredPath = configService.getCourseMaterialPath();
            if (configuredPath != null && !configuredPath.isEmpty()) {
                File configuredFolder = new File(configuredPath);
                if (configuredFolder.exists() && configuredFolder.isDirectory()) {
                    return configuredFolder;
                } else {
                    System.out.println("配置的课程材料路径不存在: " + configuredPath);
                }
            }

            // 2. 如果配置中没有，尝试使用默认路径（开发环境）
            String classPath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            File classFile = new File(classPath);

            // 如果是在开发环境（从classes目录）
            if (classFile.getPath().contains("classes")) {
                // 返回源代码中的ppt文件夹
                String projectRoot = classFile.getPath().split("build")[0];
                File defaultFolder = new File(projectRoot + "src" + File.separator + "main" + File.separator +
                               "java" + File.separator + "com" + File.separator + "javaee" + File.separator +
                               "mypilot" + File.separator + "infra" + File.separator + "rag" + File.separator + "ppt");

                if (defaultFolder.exists() && defaultFolder.isDirectory()) {
                    System.out.println("使用默认课程材料路径: " + defaultFolder.getPath());
                    return defaultFolder;
                }
            }

            // 如果是打包后的JAR环境，可以从resources中加载
            // 这里需要根据实际部署方式调整
            return null;
        } catch (Exception e) {
            System.err.println("获取PPT文件夹路径失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 根据配置创建 Embedding 服务
     */
    private EmbeddingService createEmbeddingService() {
        String serviceType = configService.getEmbeddingServiceType();
        String apiKey = configService.getEmbeddingApiKey();

        if (serviceType == null || serviceType.isEmpty()) {
            serviceType = "DashScope";
        }

        // Local不需要API Key
        if (!"Local".equals(serviceType) && (apiKey == null || apiKey.isEmpty())) {
            throw new IllegalStateException("Embedding API Key 未配置，请在设置中配置");
        }

        return switch (serviceType) {
            case "DashScope" -> new DashScopeEmbeddingService(apiKey);
            case "Zhipu" -> new ZhipuEmbeddingService(apiKey);
            case "Local" -> new LocalEmbeddingService();
            default -> new DashScopeEmbeddingService(apiKey);
        };
    }

    public static RagService getInstance(@NotNull Project project) {
        return project.getService(RagService.class);
    }

    /**
     * 处理查询请求
     * @param question 用户问题
     * @param codeContext 代码上下文（可为空）
     * @return Answer 响应
     */
    public Answer processQuery(@NotNull String question, @Nullable CodeContext codeContext) {
        try {
            if (!initialized) {
                initialize();
            }

            // 1. 构建查询（结合问题和代码上下文）
            String query = buildQuery(question, codeContext);

            // 2. 检索相关文档片段
            List<DocumentChunk> chunks = retrieveRelevantChunks(query, configService.getRetrievalTopK());

            // 3. 判断是否找到相关材料
            boolean hasRelevantMaterial = !chunks.isEmpty() &&
                    chunks.get(0).getSimilarity() >= configService.getRelevanceThreshold();

            if (!hasRelevantMaterial) {
                // 无相关材料，返回提示
                String answer = "抱歉，在知识库中没有找到相关的课程材料。\n\n您的问题：" + question;
                return new Answer(answer, new ArrayList<>(), false);
            } else {
                // 有相关材料，构建回答
                StringBuilder answerBuilder = new StringBuilder();
                answerBuilder.append("根据课程材料，以下是相关信息：\n\n");

                for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                    DocumentChunk chunk = chunks.get(i);
                    answerBuilder.append(i + 1).append(". ").append(chunk.getContent()).append("\n\n");
                    answerBuilder.append("   来源：").append(chunk.getSource());
                    answerBuilder.append("，第").append(chunk.getPageNumber()).append("页");
                    answerBuilder.append("，相似度：").append(String.format("%.2f", chunk.getSimilarity())).append("\n\n");
                }

                // 构建引用信息
                List<Citation> citations = buildCitations(chunks);

                return new Answer(answerBuilder.toString(), citations, true);
            }

        } catch (Exception e) {
            String errorMsg = "处理查询时出错: " + e.getMessage() + "\n\n请检查 RAG 配置（API Key、知识库路径等）";
            e.printStackTrace();
            return new Answer(errorMsg, new ArrayList<>(), false);
        }
    }

    /**
     * 构建查询字符串
     */
    private String buildQuery(String question, CodeContext codeContext) {
        StringBuilder query = new StringBuilder(question);

        if (codeContext != null && codeContext.getSelectedCode() != null) {
            query.append(" ").append(codeContext.getSelectedCode());
        }

        return query.toString();
    }

    /**
     * 构建引用列表
     */
    private List<Citation> buildCitations(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new Citation(
                        chunk.getSource(),
                        chunk.getPageNumber(),
                        chunk.getContent(),
                        chunk.getSimilarity()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 初始化知识库
     * @param courseMaterialFiles 课程材料文件列表（PPT, PDF, Markdown等）
     * @return 是否成功
     */
    public boolean initializeKnowledgeBase(@NotNull List<File> courseMaterialFiles) {
        if (!initialized) {
            initialize();
        }

        if (!initialized) {
            System.err.println("RAG 组件初始化失败");
            return false;
        }

        try {
            System.out.println("开始索引知识库文档...");
            List<DocumentChunk> allChunks = new ArrayList<>();

            for (File file : courseMaterialFiles) {
                try {
                    String fileName = file.getName().toLowerCase();
                    DocumentProcessor processor = null;

                    if (fileName.endsWith(".pdf")) {
                        processor = pdfDocumentProcessor;
                    } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
                        processor = pptDocumentProcessor;
                    }

                    if (processor != null) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = processor.process(file);
                        allChunks.addAll(chunks);
                        System.out.println("  - 提取 " + chunks.size() + " 个文档块");
                    } else {
                        System.out.println("跳过不支持的文件格式: " + file.getName());
                    }
                } catch (Exception e) {
                    System.err.println("处理文件失败 " + file.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if (!allChunks.isEmpty()) {
                System.out.println("索引 " + allChunks.size() + " 个文档块到向量数据库...");
                vectorDatabase.index(allChunks);
                System.out.println("知识库索引完成！");
                return true;
            } else {
                System.out.println("没有找到可索引的文档");
                return false;
            }
        } catch (Exception e) {
            System.err.println("知识库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查知识库是否已初始化
     */
    public boolean isKnowledgeBaseInitialized() {
        if (!initialized || vectorDatabase == null) {
            return false;
        }

        if (vectorDatabase instanceof LuceneVectorDatabase) {
            return ((LuceneVectorDatabase) vectorDatabase).getDocumentCount() > 0;
        }

        return false;
    }

    /**
     * 清空知识库
     */
    public void clearKnowledgeBase() {
        if (!initialized || vectorDatabase == null) {
            return;
        }

        try {
            if (vectorDatabase instanceof LuceneVectorDatabase) {
                ((LuceneVectorDatabase) vectorDatabase).clear();
                System.out.println("知识库已清空");
            }
        } catch (Exception e) {
            System.err.println("清空知识库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取知识库统计信息
     */
    public String getKnowledgeBaseStats() {
        if (!initialized || vectorDatabase == null) {
            return "RAG 系统未初始化";
        }

        try {
            if (vectorDatabase instanceof LuceneVectorDatabase) {
                int docCount = ((LuceneVectorDatabase) vectorDatabase).getDocumentCount();
                return "知识库文档数量: " + docCount;
            }
            return "无法获取统计信息";
        } catch (Exception e) {
            return "获取统计信息失败: " + e.getMessage();
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (vectorDatabase instanceof LuceneVectorDatabase) {
            ((LuceneVectorDatabase) vectorDatabase).close();
        }
    }

    /**
     * 检索相关文档片段
     * @param query 查询文本
     * @param topK 返回前 K 个最相关的片段
     * @return 文档片段列表
     */
    private List<DocumentChunk> retrieveRelevantChunks(@NotNull String query, int topK) {
        if (!initialized || retriever == null) {
            return new ArrayList<>();
        }

        try {
            return retriever.retrieve(query, topK);
        } catch (Exception e) {
            System.err.println("检索失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 异步处理聊天会话请求
     * TODO: rag
     */
    public CompletableFuture<ChatMessage> handleRequestAsync(ChatSession chatSession) {
        return null;
    }

    /**
     * 处理聊天会话请求，结合RAG知识库和代码上下文
     * @param chatSession 聊天会话，包含历史对话和可能的代码上下文
     * @return 生成的AI回复消息
     */
    public ChatMessage handleRequest(ChatSession chatSession) {
        try {
            // 确保RAG系统已初始化
            if (!initialized) {
                initialize();
            }

            // 1. 获取最后一条用户消息作为问题
            ChatMessage lastMessage = chatSession.getLastMessage();
            if (lastMessage == null || !lastMessage.isUserMessage()) {
                return createErrorMessage("无效的请求：找不到用户问题");
            }
            String question = lastMessage.getContent();

            // 2. 检查是否有代码上下文
            List<CodeContext> codeContexts = chatSession.getCodeContexts();
            boolean hasCodeContext = codeContexts != null && !codeContexts.isEmpty();
            String codeContextStr = null;

            if (hasCodeContext) {
                // 合并所有代码上下文
                StringBuilder codeBuilder = new StringBuilder();
                for (CodeContext ctx : codeContexts) {
                    if (ctx.getSelectedCode() != null) {
                        if (ctx.getFileName() != null) {
                            codeBuilder.append("// 文件: ").append(ctx.getFileName()).append("\n");
                        }
                        codeBuilder.append(ctx.getSelectedCode()).append("\n\n");
                    }
                }
                codeContextStr = codeBuilder.toString();
            }

            // 3. 构建查询（结合问题和代码上下文）
            String query = hasCodeContext ? question + " " + codeContextStr : question;

            // 4. 从知识库检索相关文档
            List<DocumentChunk> relevantChunks = retrieveRelevantChunks(query, configService.getRetrievalTopK());

            // 5. 判断是否找到相关知识
            boolean hasRelevantKnowledge = !relevantChunks.isEmpty() &&
                    relevantChunks.get(0).getSimilarity() >= configService.getRelevanceThreshold();

            // 6. 构建最终的prompt
            String ragPromptStr;
            if (hasCodeContext && hasRelevantKnowledge) {
                // 有代码上下文 + 有知识库材料
                ragPromptStr = ragPrompt.buildPromptWithCodeContext(question, codeContextStr, relevantChunks);
            } else if (hasCodeContext && !hasRelevantKnowledge) {
                // 有代码上下文 + 无知识库材料
                ragPromptStr = ragPrompt.buildPromptWithCodeContextOnly(question, codeContextStr);
            } else if (!hasCodeContext && hasRelevantKnowledge) {
                // 无代码上下文 + 有知识库材料
                ragPromptStr = ragPrompt.buildPromptWithContext(question, relevantChunks);
            } else {
                // 无代码上下文 + 无知识库材料
                ragPromptStr = ragPrompt.buildGeneralPrompt(question);
            }

            // 7. 获取历史对话的prompt（最近N条消息）
            String historyPrompt = chatSession.buildSessionContextPrompt(Chat.MAX_CHAT_TURN); // 获取最近10条消息

            // 8. 组合成总prompt: 历史对话 + RAG prompt
            String finalPrompt = historyPrompt + "\n\n" + ragPromptStr;

            // 9. 调用 LLM API 生成回答
            StringBuilder responseContent = new StringBuilder();

            try {
                String llmResponse = llmClient.chat(finalPrompt);
                responseContent.append(llmResponse);
            } catch (Exception llmError) {
                System.err.println("调用 LLM API 失败: " + llmError.getMessage());
                llmError.printStackTrace();
                // 如果 API 调用失败，返回错误信息
                responseContent.append("抱歉，调用 AI 模型时出现错误：").append(llmError.getMessage());
                responseContent.append("\n\n请检查 API Key 和网络连接是否正常。");
            }


            // 10. 添加知识来源标注
            responseContent.append("\n---\n");
            if (hasRelevantKnowledge) {
                responseContent.append("📚 知识来源：知识库材料\n");
                for (int i = 0; i < Math.min(3, relevantChunks.size()); i++) {
                    DocumentChunk chunk = relevantChunks.get(i);
                    responseContent.append(String.format("  [%d] %s (第%d页) - 相似度: %.2f\n",
                            i + 1, chunk.getSource(), chunk.getPageNumber(), chunk.getSimilarity()));
                }
            } else {
                responseContent.append("💡 知识来源：基于大模型的通用知识\n");
                responseContent.append("  注意：知识库中未找到相关的课程材料，本回答基于AI的通用知识。\n");
            }

            if (hasCodeContext) {
                responseContent.append("💻 已结合您提供的代码上下文\n");
            }

            // 11. 创建AI回复消息
            ChatMessage response = new ChatMessage(ChatMessage.Type.ASSISTANT, responseContent.toString());

            System.out.println("RAG请求处理完成 - 知识库匹配: " + hasRelevantKnowledge +
                             ", 代码上下文: " + hasCodeContext);

            return response;

        } catch (Exception e) {
            System.err.println("处理RAG请求时出错: " + e.getMessage());
            e.printStackTrace();
            return createErrorMessage("处理请求时发生错误: " + e.getMessage());
        }
    }

    /**
     * 创建错误消息
     */
    private ChatMessage createErrorMessage(String errorMsg) {
        return new ChatMessage(ChatMessage.Type.ASSISTANT, "❌ " + errorMsg);
    }
}
