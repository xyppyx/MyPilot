package com.javaee.mypilot.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.Chat;
import com.javaee.mypilot.core.model.chat.ChatMessage;
import com.javaee.mypilot.core.model.chat.ChatSession;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.rag.DocumentChunk;
import com.javaee.mypilot.infra.api.LlmClient;
import com.javaee.mypilot.infra.api.RagPrompt;
import com.javaee.mypilot.infra.rag.Retriever;
import com.javaee.mypilot.infra.rag.document.*;
import com.javaee.mypilot.infra.rag.embedding.DashScopeEmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.EmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.LocalEmbeddingService;
import com.javaee.mypilot.infra.rag.embedding.ZhipuEmbeddingService;
import com.javaee.mypilot.infra.rag.vector.LuceneVectorDatabase;
import com.javaee.mypilot.infra.rag.vector.VectorDatabase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private DocumentProcessor docDocumentProcessor;
    private DocumentProcessor txtDocumentProcessor;
    private DocumentProcessor markdownDocumentProcessor;
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
            this.docDocumentProcessor = new DOCDocumentProcessor(embeddingService);
            this.txtDocumentProcessor = new TXTDocumentProcessor(embeddingService);
            this.markdownDocumentProcessor = new MarkdownDocumentProcessor(embeddingService);

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
     * 重新初始化 RAG 系统（当配置更改时调用）
     * 用于在 embedding 服务类型或 API Key 更改后更新服务
     */
    public void reinitialize() {
        System.out.println("RagService: 检测到配置更改，重新初始化...");
        
        try {
            // 先关闭旧的资源，释放 Lucene 索引锁
            closeResources();
            
            // 短暂延迟，确保 Lucene 索引锁完全释放
            // 这可以避免在快速重新初始化时出现锁冲突
            try {
                Thread.sleep(100); // 100ms 延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 重置初始化状态
            this.initialized = false;
            
            // 重新初始化（会重新创建 embedding 服务和相关的文档处理器）
            initialize();
            
            System.out.println("RagService: 重新初始化完成");
        } catch (Exception e) {
            System.err.println("重新初始化 RagService 失败: " + e.getMessage());
            e.printStackTrace();
            // 即使失败也重置初始化状态，允许下次重试
            this.initialized = false;
        }
    }

    /**
     * 关闭所有资源
     */
    private void closeResources() {
        try {
            // 关闭向量数据库（这会释放 Lucene 索引锁）
            if (vectorDatabase instanceof LuceneVectorDatabase) {
                ((LuceneVectorDatabase) vectorDatabase).close();
            }
            
            // 清空引用
            this.vectorDatabase = null;
            this.embeddingService = null;
            this.retriever = null;
            this.pptDocumentProcessor = null;
            this.pdfDocumentProcessor = null;
            this.docDocumentProcessor = null;
            this.txtDocumentProcessor = null;
            this.markdownDocumentProcessor = null;
            this.ragPrompt = null;
            
            System.out.println("RagService: 已关闭所有资源");
        } catch (Exception e) {
            System.err.println("关闭 RagService 资源时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 自动从资源中提取并加载知识库
     */
    private void autoLoadKnowledgeBase() {
        try {
            // 检查知识库是否已有内容
            if (isKnowledgeBaseInitialized()) {
                System.out.println("知识库已存在，跳过自动加载");
                return;
            }

            // 从 JAR 资源中提取课程材料到用户目录
            File materialDir = extractCourseMaterialsFromResources();
            if (materialDir == null || !materialDir.exists()) {
                System.out.println("无法提取课程材料，跳过自动加载");
                return;
            }

            // 收集所有课程材料文件
            List<File> materialFiles = new ArrayList<>();
            File[] files = materialDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".pdf") || fileName.endsWith(".ppt") || fileName.endsWith(".pptx") ||
                        fileName.endsWith(".doc") || fileName.endsWith(".docx") ||
                        fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                        materialFiles.add(file);
                    }
                }
            }

            if (materialFiles.isEmpty()) {
                System.out.println("未找到课程材料文件");
                return;
            }

            System.out.println("发现 " + materialFiles.size() + " 个课程材料文件，开始自动索引...");
            // 显式指定静态资源类型
            boolean success = initializeKnowledgeBase(materialFiles, DocumentChunk.SourceType.STATIC);
            if (success) {
                System.out.println("知识库自动加载完成！");
            }
        } catch (Exception e) {
            System.err.println("自动加载知识库时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从 JAR 资源中提取课程材料到用户目录
     * @return 课程材料目录
     */
    private File extractCourseMaterialsFromResources() {
        try {
            // 用户目录下的课程材料文件夹
            String userHome = System.getProperty("user.home");
            File materialDir = new File(userHome + File.separator + ".mypilot" + File.separator + "courseMaterials");

            // 如果已经提取过，直接返回
            if (materialDir.exists() && materialDir.list() != null && materialDir.list().length > 0) {
                System.out.println("课程材料已存在: " + materialDir.getPath());
                return materialDir;
            }

            // 创建目录
            if (!materialDir.exists()) {
                boolean created = materialDir.mkdirs();
                if (!created) {
                    System.err.println("无法创建课程材料目录: " + materialDir.getPath());
                    return null;
                }
            }

            // 从 resources/courseMaterials/ppt 目录提取文件
            ClassLoader classLoader = getClass().getClassLoader();
            String resourcePath = "courseMaterials/ppt/";

            // 获取资源目录下的所有文件
            List<String> fileNames = scanResourceDirectory(resourcePath);
            if (fileNames.isEmpty()) {
                System.out.println("未在资源目录 " + resourcePath + " 中找到课程材料文件");
                return materialDir;
            }

            System.out.println("发现 " + fileNames.size() + " 个课程材料文件待提取");

            // 提取每个文件
            int extractedCount = 0;
            for (String fileName : fileNames) {
                try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath + fileName)) {
                    if (inputStream != null) {
                        File outputFile = new File(materialDir, fileName);
                        java.nio.file.Files.copy(inputStream, outputFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("提取文件: " + fileName);
                        extractedCount++;
                    } else {
                        System.out.println("资源文件不存在: " + fileName);
                    }
                } catch (Exception e) {
                    System.err.println("提取文件失败 " + fileName + ": " + e.getMessage());
                }
            }

            System.out.println("成功提取 " + extractedCount + " 个课程材料文件");
            return materialDir;
        } catch (Exception e) {
            System.err.println("提取课程材料失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 扫描资源目录，获取所有 PPT/PDF 文件
     * @param resourcePath 资源路径（如 "courseMaterials/ppt/"）
     * @return 文件名列表
     */
    private List<String> scanResourceDirectory(String resourcePath) {
        List<String> fileNames = new ArrayList<>();
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            if (classLoader == null) {
                System.err.println("ClassLoader 为 null");
                return fileNames;
            }

            // 尝试方法1：通过 getResource 获取目录的 URL
            java.net.URL resourceUrl = classLoader.getResource(resourcePath);
            
            if (resourceUrl == null) {
                // 尝试不带尾部斜杠的路径
                String altPath = resourcePath.endsWith("/") ? 
                    resourcePath.substring(0, resourcePath.length() - 1) : resourcePath;
                resourceUrl = classLoader.getResource(altPath);
                
                if (resourceUrl == null) {
                    System.out.println("无法找到资源目录: " + resourcePath + " 或 " + altPath);
                    // 尝试列出所有可能的资源
                    debugResourceSearch(classLoader, "courseMaterials");
                    return fileNames;
                }
            }

            String protocol = resourceUrl.getProtocol();
            System.out.println("找到资源 URL: " + resourceUrl + ", 协议: " + protocol);

            if ("file".equals(protocol)) {
                // 开发环境：直接读取文件系统
                try {
                    File dir = new File(resourceUrl.toURI());
                    System.out.println("尝试访问文件目录: " + dir.getAbsolutePath() + ", 存在: " + dir.exists());
                    
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        System.out.println("目录中的文件数量: " + (files != null ? files.length : 0));
                        
                        if (files != null) {
                            for (File file : files) {
                                String fileName = file.getName();
                                System.out.println("检查文件: " + fileName + ", 是文件: " + file.isFile() + ", 支持: " + isSupportedFile(fileName));
                                if (file.isFile() && isSupportedFile(fileName)) {
                                    fileNames.add(fileName);
                                    System.out.println("添加文件: " + fileName);
                                }
                            }
                        }
                    } else {
                        System.err.println("目录不存在或不是目录: " + dir.getAbsolutePath());
                    }
                } catch (java.net.URISyntaxException e) {
                    System.err.println("URI 转换失败: " + e.getMessage());
                    // 尝试使用 URL.getPath() 作为备选方案
                    try {
                        String path = resourceUrl.getPath();
                        // 处理 URL 编码（如 %20 表示空格）
                        path = java.net.URLDecoder.decode(path, "UTF-8");
                        // Windows 下可能需要移除开头的 /file:/
                        if (path.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                            if (path.length() > 1 && path.charAt(1) == ':') {
                                path = path.substring(1);
                            }
                        }
                        File dir = new File(path);
                        System.out.println("使用备用路径: " + dir.getAbsolutePath());
                        if (dir.exists() && dir.isDirectory()) {
                            File[] files = dir.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.isFile() && isSupportedFile(file.getName())) {
                                        fileNames.add(file.getName());
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("备用路径也失败: " + ex.getMessage());
                    }
                }
            } else if ("jar".equals(protocol)) {
                // 生产环境：从 JAR 中读取
                try {
                    java.net.JarURLConnection jarConnection = (java.net.JarURLConnection) resourceUrl.openConnection();
                    java.util.jar.JarFile jarFile = jarConnection.getJarFile();
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();

                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        // 检查是否在目标目录下，且是支持的文件格式
                        if (entryName.startsWith(resourcePath) && !entry.isDirectory()) {
                            String fileName = entryName.substring(resourcePath.length());
                            // 排除子目录中的文件
                            if (!fileName.contains("/") && isSupportedFile(fileName)) {
                                fileNames.add(fileName);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("读取 JAR 文件失败: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("未知的 URL 协议: " + protocol);
            }
            
            System.out.println("扫描完成，找到 " + fileNames.size() + " 个文件");
        } catch (Exception e) {
            System.err.println("扫描资源目录失败: " + e.getMessage());
            e.printStackTrace();
        }
        return fileNames;
    }

    /**
     * 调试方法：搜索资源目录
     */
    private void debugResourceSearch(ClassLoader classLoader, String basePath) {
        try {
            System.out.println("开始调试资源搜索，基础路径: " + basePath);
            
            // 尝试不同的路径变体
            String[] variations = {
                basePath,
                basePath + "/",
                "/" + basePath,
                "/" + basePath + "/"
            };
            
            for (String path : variations) {
                java.net.URL url = classLoader.getResource(path);
                if (url != null) {
                    System.out.println("找到资源: " + path + " -> " + url);
                }
            }
            
            // 尝试直接列出所有资源
            try {
                java.net.URL baseUrl = classLoader.getResource(basePath);
                if (baseUrl != null && "file".equals(baseUrl.getProtocol())) {
                    File baseDir = new File(baseUrl.toURI());
                    if (baseDir.exists()) {
                        System.out.println("基础目录存在: " + baseDir.getAbsolutePath());
                        File[] subDirs = baseDir.listFiles();
                        if (subDirs != null) {
                            for (File subDir : subDirs) {
                                System.out.println("  子目录/文件: " + subDir.getName() + (subDir.isDirectory() ? " (目录)" : " (文件)"));
                                if (subDir.isDirectory() && "ppt".equals(subDir.getName())) {
                                    File[] pptFiles = subDir.listFiles();
                                    if (pptFiles != null) {
                                        System.out.println("    PPT 目录中的文件:");
                                        for (File f : pptFiles) {
                                            System.out.println("      - " + f.getName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("调试资源搜索时出错: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("调试资源搜索失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否是支持的文件格式
     */
    private boolean isSupportedFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".ppt") ||
               lowerName.endsWith(".pptx") ||
               lowerName.endsWith(".pdf") ||
               lowerName.endsWith(".doc") ||
               lowerName.endsWith(".docx") ||
               lowerName.endsWith(".txt") ||
               lowerName.endsWith(".md");
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
     * 初始化知识库
     * @param courseMaterialFiles 课程材料文件列表（PPT, PDF 等）
     * @param sourceType 文档来源类型（STATIC 或 USER_UPLOADED）
     * @return 是否成功
     */
    public boolean initializeKnowledgeBase(@NotNull List<File> courseMaterialFiles, DocumentChunk.SourceType sourceType) {
        if (!initialized) {
            initialize();
        }

        if (!initialized) {
            System.err.println("RAG 组件初始化失败");
            return false;
        }

        try {
            String sourceTypeName = sourceType == DocumentChunk.SourceType.STATIC ? "静态资源" : "用户上传";
            System.out.println("开始索引" + sourceTypeName + "文档...");
            List<DocumentChunk> allChunks = new ArrayList<>();

            for (File file : courseMaterialFiles) {
                try {
                    String fileName = file.getName().toLowerCase();

                    if (fileName.endsWith(".pdf")) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = pdfDocumentProcessor.process(file, sourceType);
                        allChunks.addAll(chunks);
                        System.out.println("  - 提取 " + chunks.size() + " 个文档块");
                    } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = pptDocumentProcessor.process(file, sourceType);
                        allChunks.addAll(chunks);
                        System.out.println("  - 提取 " + chunks.size() + " 个文档块");
                    } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = docDocumentProcessor.process(file, sourceType);
                        allChunks.addAll(chunks);
                        System.out.println("  - 提取 " + chunks.size() + " 个文档块");
                    } else if (fileName.endsWith(".txt")) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = txtDocumentProcessor.process(file, sourceType);
                        allChunks.addAll(chunks);
                        System.out.println("  - 提取 " + chunks.size() + " 个文档块");
                    } else if (fileName.endsWith(".md")) {
                        System.out.println("处理文件: " + file.getName());
                        List<DocumentChunk> chunks = markdownDocumentProcessor.process(file, sourceType);
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
                System.out.println("索引 " + allChunks.size() + " 个" + sourceTypeName + "文档块到向量数据库...");
                vectorDatabase.index(allChunks);
                System.out.println(sourceTypeName + "知识库索引完成！");
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
     * 用户上传文件到知识库
     * @param files 用户上传的文件列表
     * @return 是否成功
     */
    public boolean uploadFilesToKnowledgeBase(@NotNull List<File> files) {
        if (!initialized) {
            initialize();
        }

        try {
            // 创建用户上传目录
            String userUploadPath = configService.getUserUploadPath();
            File uploadDir = new File(userUploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (!created) {
                    System.err.println("无法创建用户上传目录: " + userUploadPath);
                    return false;
                }
            }

            // 复制文件到用户上传目录
            List<File> copiedFiles = new ArrayList<>();
            for (File file : files) {
                try {
                    File targetFile = new File(uploadDir, file.getName());
                    java.nio.file.Files.copy(file.toPath(), targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    copiedFiles.add(targetFile);
                    System.out.println("已复制用户文件: " + file.getName());
                } catch (Exception e) {
                    System.err.println("复制文件失败 " + file.getName() + ": " + e.getMessage());
                }
            }

            if (copiedFiles.isEmpty()) {
                System.out.println("没有成功复制任何文件");
                return false;
            }

            // 使用 USER_UPLOADED 类型索引这些文件
            boolean success = initializeKnowledgeBase(copiedFiles, DocumentChunk.SourceType.USER_UPLOADED);
            if (success) {
                System.out.println("成功上传并索引 " + copiedFiles.size() + " 个用户文件");
            }
            return success;

        } catch (Exception e) {
            System.err.println("上传文件到知识库失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 用户上传文件夹到知识库
     * @param folder 用户上传的文件夹
     * @return 是否成功
     */
    public boolean uploadFolderToKnowledgeBase(@NotNull File folder) {
        if (!folder.isDirectory()) {
            System.err.println("指定的路径不是文件夹: " + folder.getPath());
            return false;
        }

        List<File> files = new ArrayList<>();
        collectSupportedFiles(folder, files);

        if (files.isEmpty()) {
            System.out.println("文件夹中没有找到支持的文件格式");
            return false;
        }

        System.out.println("在文件夹中发现 " + files.size() + " 个支持的文件");
        return uploadFilesToKnowledgeBase(files);
    }

    /**
     * 递归收集文件夹中所有支持的文件
     */
    private void collectSupportedFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectSupportedFiles(file, result);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".pdf") || fileName.endsWith(".ppt") || fileName.endsWith(".pptx") ||
                    fileName.endsWith(".doc") || fileName.endsWith(".docx") ||
                    fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                    result.add(file);
                }
            }
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
     * 清空知识库（删除所有文档）
     */
    public void clearKnowledgeBase() {
        clearKnowledgeBase(null);
    }

    /**
     * 根据来源类型清空知识库
     * @param sourceType 文档来源类型（null 表示删除所有文档，STATIC 表示只删除静态资源，USER_UPLOADED 表示只删除用户上传的文档）
     */
    public void clearKnowledgeBase(DocumentChunk.SourceType sourceType) {
        if (!initialized || vectorDatabase == null) {
            return;
        }

        try {
            if (vectorDatabase instanceof LuceneVectorDatabase) {
                ((LuceneVectorDatabase) vectorDatabase).clear(sourceType);
                if (sourceType == null) {
                    System.out.println("知识库已完全清空");
                } else {
                    String typeName = sourceType == DocumentChunk.SourceType.STATIC ? "静态资源" : "用户上传";
                    System.out.println("已清空知识库中的" + typeName + "文档");
                }
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
     * 获取知识库中的所有文件列表
     * 
     * 接口定义：供 UI 层调用
     * 
     * 注意：此方法依赖 LuceneVectorDatabase.getAllFiles() 方法
     * 后端 RAG 同学需要在 LuceneVectorDatabase 中实现 getAllFiles() 方法
     * 
     * @return 文件信息列表
     */
    public List<LuceneVectorDatabase.FileInfo> getKnowledgeBaseFiles() {
        // UI 接口：调用底层 LuceneVectorDatabase.getAllFiles() 获取文件列表
        // TODO: 等待后端实现 LuceneVectorDatabase.getAllFiles() 方法
        if (!initialized || vectorDatabase == null) {
            return new ArrayList<>();
        }

        if (vectorDatabase instanceof LuceneVectorDatabase) {
            return ((LuceneVectorDatabase) vectorDatabase).getAllFiles();
        }
        return new ArrayList<>();
    }

    /**
     * 从知识库删除指定文件
     * 
     * 接口定义：供 UI 层调用
     * 
     * 注意：此方法依赖 LuceneVectorDatabase.deleteBySource() 方法
     * 后端 RAG 同学需要在 LuceneVectorDatabase 中实现 deleteBySource(String source) 方法
     * 
     * @param fileName 文件名
     * @return 是否成功（删除的文档数量 > 0 表示成功）
     */
    public boolean deleteFileFromKnowledgeBase(String fileName) {
        // UI 接口：调用底层 LuceneVectorDatabase.deleteBySource() 删除文件
        // TODO: 等待后端实现 LuceneVectorDatabase.deleteBySource() 方法
        if (!initialized || vectorDatabase == null) {
            return false;
        }

        if (vectorDatabase instanceof LuceneVectorDatabase) {
            int deletedCount = ((LuceneVectorDatabase) vectorDatabase).deleteBySource(fileName);
            System.out.println("已从知识库删除文件: " + fileName + " (删除了 " + deletedCount + " 个文档块)");
            return deletedCount > 0;
        }
        return false;
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
     * 策略：
     * - 轻量级操作（解析、字符串拼接）同步执行
     * - 耗时的I/O操作（向量检索、LLM API调用）异步并行执行
     *
     * @param chatSession 聊天会话
     * @return 异步的 ChatMessage 结果
     */
    public CompletableFuture<ChatMessage> handleRequestAsync(ChatSession chatSession) {
        // 确保RAG系统已初始化
        if (!initialized) {
            initialize();
        }

        // 步骤1: 同步解析用户输入（轻量级操作，耗时 < 1ms）
        UserQueryContext queryContext = parseUserQuery(chatSession);
        if (queryContext.hasError) {
            return CompletableFuture.completedFuture(
                createErrorMessage(queryContext.errorMessage)
            );
        }

        // 步骤2 & 3: 并行执行两个耗时的I/O操作
        // 任务A: 异步从知识库检索相关文档（磁盘I/O，耗时 10-100ms）
        CompletableFuture<List<DocumentChunk>> retrievalFuture =
            CompletableFuture.supplyAsync(() -> retrieveDocuments(queryContext));

        // 任务B: 异步构建历史对话上下文（内存操作，耗时 1-10ms）
        CompletableFuture<String> historyFuture =
            CompletableFuture.supplyAsync(() -> buildHistoryPrompt(chatSession));

        // 步骤4: 等待任务A和任务B完成，然后构建最终的prompt和调用LLM
        return retrievalFuture.thenCombine(historyFuture, (chunks, history) -> {
                // 同步构建 RAG Prompt（纯内存操作，快速）
                PromptBuildResult promptResult = buildPromptWithContext(queryContext, chunks);
                return new PromptAndHistory(promptResult, history);
            })
            .thenCompose(pair ->
                // 步骤5: 异步调用 LLM API（网络I/O，最耗时：1-5秒）
                CompletableFuture.supplyAsync(() ->
                    callLlmApi(pair.promptResult, pair.historyPrompt)
                )
            )
            .thenApply(llmResponse ->
                // 步骤6: 同步组装最终响应（纯内存操作，快速）
                assembleResponse(queryContext, llmResponse)
            )
            .exceptionally(this::handleAsyncError);
    }

    /**
     * 解析用户查询和代码上下文
     */
    private UserQueryContext parseUserQuery(ChatSession chatSession) {
        try {
            // 获取最后一条用户消息作为问题
            ChatMessage lastMessage = chatSession.getLastMessage();
            if (lastMessage == null || !lastMessage.isUserMessage()) {
                return new UserQueryContext("无效的请求：找不到用户问题", true);
            }
            String question = lastMessage.getContent();

            // 检查是否有代码上下文
            List<CodeContext> codeContexts = chatSession.getCodeContexts();
            boolean hasCodeContext = codeContexts != null && !codeContexts.isEmpty();
            String codeContextStr = null;

            if (hasCodeContext) {
                // 合并所有代码上下文
                StringBuilder codeBuilder = new StringBuilder();
                for (CodeContext ctx : codeContexts) {
                    codeBuilder.append(ctx.formatContext());
                }
                codeContextStr = codeBuilder.toString();
            }

            return new UserQueryContext(question, codeContextStr, hasCodeContext);
        } catch (Exception e) {
            return new UserQueryContext("解析用户查询失败: " + e.getMessage(), true);
        }
    }

    /**
     * 从知识库检索相关文档
     */
    private List<DocumentChunk> retrieveDocuments(UserQueryContext queryContext) {
        if (queryContext.hasError) {
            return new ArrayList<>();
        }

        String query = queryContext.hasCodeContext ?
            queryContext.question + " " + queryContext.codeContextStr :
            queryContext.question;

        return retrieveRelevantChunks(query, configService.getRetrievalTopK());
    }

    /**
     * 构建历史对话上下文 Prompt
     */
    private String buildHistoryPrompt(ChatSession chatSession) {
        return chatSession.buildSessionContextPrompt(Chat.MAX_CHAT_TURN);
    }

    /**
     * 根据查询上下文和检索结果构建 Prompt
     */
    private PromptBuildResult buildPromptWithContext(UserQueryContext queryContext,
                                                      List<DocumentChunk> relevantChunks) {
        if (queryContext.hasError) {
            return new PromptBuildResult(queryContext.errorMessage, true, false, relevantChunks);
        }

        // 判断是否找到相关知识
        boolean hasRelevantKnowledge = !relevantChunks.isEmpty() &&
            relevantChunks.get(0).getSimilarity() >= configService.getRelevanceThreshold();

        // 构建RAG prompt
        String ragPromptStr = buildRagPrompt(
            queryContext.question,
            queryContext.codeContextStr,
            queryContext.hasCodeContext,
            relevantChunks,
            hasRelevantKnowledge
        );

        return new PromptBuildResult(ragPromptStr, false, hasRelevantKnowledge, relevantChunks);
    }

    /**
     * 调用 LLM API 生成回答
     */
    private LlmResponse callLlmApi(PromptBuildResult promptResult, String historyPrompt) {
        if (promptResult.hasError) {
            return new LlmResponse(promptResult.content, true,
                promptResult.hasRelevantKnowledge, promptResult.relevantChunks);
        }

        String finalPrompt = historyPrompt + "\n\n" + promptResult.content;

        try {
            String llmResponse = llmClient.chat(finalPrompt);
            return new LlmResponse(llmResponse, false,
                promptResult.hasRelevantKnowledge, promptResult.relevantChunks);
        } catch (Exception e) {
            System.err.println("调用 LLM API 失败: " + e.getMessage());
            e.printStackTrace();
            String errorMsg = "抱歉，调用 AI 模型时出现错误：" + e.getMessage() +
                "\n\n请检查 API Key 和网络连接是否正常。";
            return new LlmResponse(errorMsg, true,
                promptResult.hasRelevantKnowledge, promptResult.relevantChunks);
        }
    }

    /**
     * 组装最终的响应消息
     */
    private ChatMessage assembleResponse(UserQueryContext queryContext, LlmResponse llmResponse) {
        StringBuilder responseContent = new StringBuilder();
        responseContent.append(llmResponse.content);

        // 添加知识来源标注
        responseContent.append("\n---\n");
        if (llmResponse.hasRelevantKnowledge) {
            responseContent.append("📚 知识来源：知识库材料\n");
            for (int i = 0; i < Math.min(3, llmResponse.relevantChunks.size()); i++) {
                DocumentChunk chunk = llmResponse.relevantChunks.get(i);
                responseContent.append(String.format("  [%d] %s (第%d页) - 相似度: %.2f\n",
                        i + 1, chunk.getSource(), chunk.getPageNumber(), chunk.getSimilarity()));
            }
        } else {
            responseContent.append("💡 知识来源：基于大模型的通用知识\n");
            responseContent.append("  注意：知识库中未找到相关的课程材料，本回答基于AI的通用知识。\n");
        }

        if (queryContext.hasCodeContext) {
            responseContent.append("💻 已结合您提供的代码上下文\n");
        }

        System.out.println("RAG异步请求处理完成 - 知识库匹配: " + llmResponse.hasRelevantKnowledge +
                         ", 代码上下文: " + queryContext.hasCodeContext);

        return new ChatMessage(ChatMessage.Type.ASSISTANT, responseContent.toString());
    }

    /**
     * 处理异步执行中的错误
     */
    private ChatMessage handleAsyncError(Throwable throwable) {
        System.err.println("异步处理RAG请求时出错: " + throwable.getMessage());
        throwable.printStackTrace();
        return createErrorMessage("处理请求时发生错误: " + throwable.getMessage());
    }

    /**
     * 构建 RAG Prompt
     */
    private String buildRagPrompt(String question, String codeContextStr, boolean hasCodeContext,
                                   List<DocumentChunk> relevantChunks, boolean hasRelevantKnowledge) {
        if (hasCodeContext && hasRelevantKnowledge) {
            // 有代码上下文 + 有知识库材料
            return ragPrompt.buildPromptWithCodeContext(question, codeContextStr, relevantChunks);
        } else if (hasCodeContext && !hasRelevantKnowledge) {
            // 有代码上下文 + 无知识库材料
            return ragPrompt.buildPromptWithCodeContextOnly(question, codeContextStr);
        } else if (!hasCodeContext && hasRelevantKnowledge) {
            // 无代码上下文 + 有知识库材料
            return ragPrompt.buildPromptWithContext(question, relevantChunks);
        } else {
            // 无代码上下文 + 无知识库材料
            return ragPrompt.buildGeneralPrompt(question);
        }
    }

    /**
     * 用户查询上下文（内部类）
     */
    private static class UserQueryContext {
        final String question;
        final String codeContextStr;
        final boolean hasCodeContext;
        final boolean hasError;
        final String errorMessage;

        UserQueryContext(String question, String codeContextStr, boolean hasCodeContext) {
            this.question = question;
            this.codeContextStr = codeContextStr;
            this.hasCodeContext = hasCodeContext;
            this.hasError = false;
            this.errorMessage = null;
        }

        UserQueryContext(String errorMessage, boolean hasError) {
            this.errorMessage = errorMessage;
            this.hasError = hasError;
            this.question = null;
            this.codeContextStr = null;
            this.hasCodeContext = false;
        }
    }

    /**
     * Prompt 构建结果（内部类）
     */
    private static class PromptBuildResult {
        final String content;
        final boolean hasError;
        final boolean hasRelevantKnowledge;
        final List<DocumentChunk> relevantChunks;

        PromptBuildResult(String content, boolean hasError, boolean hasRelevantKnowledge, List<DocumentChunk> relevantChunks) {
            this.content = content;
            this.hasError = hasError;
            this.hasRelevantKnowledge = hasRelevantKnowledge;
            this.relevantChunks = relevantChunks;
        }
    }

    /**
     * LLM 响应结果（内部类）
     */
    private static class LlmResponse {
        final String content;
        final boolean hasError;
        final boolean hasRelevantKnowledge;
        final List<DocumentChunk> relevantChunks;

        LlmResponse(String content, boolean hasError, boolean hasRelevantKnowledge, List<DocumentChunk> relevantChunks) {
            this.content = content;
            this.hasError = hasError;
            this.hasRelevantKnowledge = hasRelevantKnowledge;
            this.relevantChunks = relevantChunks;
        }
    }

    /**
     * Prompt 和历史上下文的组合（内部类）
     * 用于在异步流程中传递中间结果
     */
    private static class PromptAndHistory {
        final PromptBuildResult promptResult;
        final String historyPrompt;

        PromptAndHistory(PromptBuildResult promptResult, String historyPrompt) {
            this.promptResult = promptResult;
            this.historyPrompt = historyPrompt;
        }
    }

    /**
     * 创建错误消息
     */
    private ChatMessage createErrorMessage(String errorMsg) {
        return new ChatMessage(ChatMessage.Type.ASSISTANT, "❌ " + errorMsg);
    }
}
