package com.javaee.mypilot.service;

import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.model.chat.CodeContext;
import com.javaee.mypilot.core.model.rag.Answer;
import com.javaee.mypilot.core.model.rag.DocumentChunk;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RagService 单元测试
 * 测试 RAG (Retrieval-Augmented Generation) 服务的主要功能
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagServiceTest {

    @Mock
    private Project mockProject;

    private RagService ragService;
    private ConfigService configService;
    private Path tempDir;
    private File testTxtFile;
    private File testMdFile;

    @BeforeAll
    void setUpAll() throws IOException {
        MockitoAnnotations.openMocks(this);

        // 创建临时目录用于测试
        tempDir = Files.createTempDirectory("ragservice-test-");
        System.out.println("测试临时目录: " + tempDir.toString());
    }

    @BeforeEach
    void setUp() throws IOException {
        // 模拟 Project 和 ConfigService
        mockProject = mock(Project.class);
        configService = mock(ConfigService.class);

        // 配置 mock 返回值
        when(mockProject.getService(ConfigService.class)).thenReturn(configService);

        // 设置测试配置
        String testIndexPath = tempDir.resolve("vector_index").toString();
        String testUploadPath = tempDir.resolve("userUploads").toString();

        when(configService.getKnowledgeBasePath()).thenReturn(testIndexPath);
        when(configService.getUserUploadPath()).thenReturn(testUploadPath);
        when(configService.getEmbeddingServiceType()).thenReturn("DashScope"); // 使用真实服务
        when(configService.getEmbeddingApiKey()).thenReturn("sk-12ffff37c0834dfd8d227eda0b809f91"); //
        when(configService.getRetrievalTopK()).thenReturn(5);
        when(configService.getRelevanceThreshold()).thenReturn(0.7);

        // 创建测试文件
        createTestFiles();

        // 初始化 RagService
        ragService = new RagService(mockProject);
    }

    @AfterEach
    void tearDown() {
        // 关闭 RagService 资源
        if (ragService != null) {
            ragService.close();
        }
    }

    @AfterAll
    void tearDownAll() throws IOException {
        // 清理临时目录
        if (tempDir != null && Files.exists(tempDir)) {
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * 创建测试文件
     */
    private void createTestFiles() throws IOException {
        // 创建测试 TXT 文件
        testTxtFile = tempDir.resolve("test-java-basics.txt").toFile();
        try (FileWriter writer = new FileWriter(testTxtFile)) {
            writer.write("Java 基础知识\n\n");
            writer.write("Java 是一种面向对象的编程语言。\n");
            writer.write("Java 的主要特点包括：平台无关性、面向对象、安全性、多线程支持等。\n");
            writer.write("Java 虚拟机（JVM）是 Java 平台无关性的核心。\n");
            writer.write("Java 的基本数据类型包括：int, double, boolean, char 等。\n");
        }

        // 创建测试 Markdown 文件
        testMdFile = tempDir.resolve("test-java-oop.md").toFile();
        try (FileWriter writer = new FileWriter(testMdFile)) {
            writer.write("# Java 面向对象编程\n\n");
            writer.write("## 类和对象\n\n");
            writer.write("类是对象的模板，对象是类的实例。\n\n");
            writer.write("## 继承\n\n");
            writer.write("继承是面向对象编程的重要特性，子类可以继承父类的属性和方法。\n\n");
            writer.write("## 多态\n\n");
            writer.write("多态允许不同类的对象对同一消息做出响应。\n");
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    @Test
    @DisplayName("测试1: RagService 初始化")
    void testInitialize() {
        // 执行初始化
        ragService.initialize();

        // 验证知识库统计信息可用
        String stats = ragService.getKnowledgeBaseStats();
        assertNotNull(stats, "统计信息不应为空");
        assertTrue(stats.contains("知识库文档数量") || stats.contains("RAG 系统未初始化"),
                  "统计信息应包含文档数量信息");

        System.out.println("✓ 测试1通过: RagService 成功初始化");
    }

    @Test
    @DisplayName("测试2: 初始化知识库 - 使用测试文件")
    void testInitializeKnowledgeBase() {
        // 初始化 RagService
        ragService.initialize();

        // 准备测试文件列表
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        testFiles.add(testMdFile);

        // 执行知识库初始化
        boolean success = ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 验证结果
        assertTrue(success, "知识库初始化应该成功");
        assertTrue(ragService.isKnowledgeBaseInitialized(), "知识库应该被标记为已初始化");

        // 验证统计信息
        String stats = ragService.getKnowledgeBaseStats();
        assertTrue(stats.contains("知识库文档数量"), "应该包含文档数量信息");
        System.out.println("知识库统计: " + stats);

        System.out.println("✓ 测试2通过: 知识库成功初始化，文件已索引");
    }

    @Test
    @DisplayName("测试3: 查询功能 - 无代码上下文")
    void testProcessQueryWithoutCodeContext() {
        // 初始化并加载知识库
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        testFiles.add(testMdFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 执行查询
        String question = "什么是Java虚拟机？";
        Answer answer = ragService.processQuery(question, null);

        // 验证答案
        assertNotNull(answer, "答案不应为空");
        assertNotNull(answer.getContent(), "答案内容不应为空");
        assertTrue(answer.getContent().length() > 0, "答案应该有内容");

        System.out.println("问题: " + question);
        System.out.println("答案: " + answer.getContent());
        System.out.println("是否来自课程材料: " + answer.isFromCourse());

        // 如果找到相关材料，验证引用信息
        if (answer.isFromCourse()) {
            assertNotNull(answer.getCitations(), "引用列表不应为空");
            assertTrue(answer.getCitations().size() > 0, "应该有引用信息");
            System.out.println("引用数量: " + answer.getCitations().size());
        }

        System.out.println("✓ 测试3通过: 查询功能正常工作");
    }

    @Test
    @DisplayName("测试4: 查询功能 - 带代码上下文")
    void testProcessQueryWithCodeContext() {
        // 初始化并加载知识库
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        testFiles.add(testMdFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 创建代码上下文
        CodeContext codeContext = new CodeContext(
            "public class Person { private String name; }",
            "Person.java",
            "Java",
            10
        );

        // 执行查询
        String question = "这段代码体现了什么面向对象特性？";
        Answer answer = ragService.processQuery(question, codeContext);

        // 验证答案
        assertNotNull(answer, "答案不应为空");
        assertNotNull(answer.getContent(), "答案内容不应为空");

        System.out.println("问题: " + question);
        System.out.println("代码上下文: " + codeContext.getSelectedCode());
        System.out.println("答案: " + answer.getContent());

        System.out.println("✓ 测试4通过: 带代码上下文的查询功能正常工作");
    }

    @Test
    @DisplayName("测试5: 查询不存在的知识")
    void testProcessQueryWithNoRelevantKnowledge() {
        // 初始化并加载知识库
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 查询完全不相关的问题
        String question = "如何做意大利面？";
        Answer answer = ragService.processQuery(question, null);

        // 验证答案
        assertNotNull(answer, "答案不应为空");
        assertFalse(answer.isFromCourse(), "不应该找到相关课程材料");
        assertTrue(answer.getContent().contains("没有找到相关"), "应该提示没有找到相关材料");

        System.out.println("问题: " + question);
        System.out.println("答案: " + answer.getContent());

        System.out.println("✓ 测试5通过: 正确处理不相关的查询");
    }

    @Test
    @DisplayName("测试6: 上传文件到知识库")
    void testUploadFilesToKnowledgeBase() throws IOException {
        // 初始化 RagService
        ragService.initialize();

        // 创建额外的测试文件
        File userFile = tempDir.resolve("user-upload.txt").toFile();
        try (FileWriter writer = new FileWriter(userFile)) {
            writer.write("用户上传的文档内容\n");
            writer.write("这是用户自己提供的学习材料。\n");
        }

        // 上传文件
        List<File> filesToUpload = new ArrayList<>();
        filesToUpload.add(userFile);
        boolean success = ragService.uploadFilesToKnowledgeBase(filesToUpload);

        // 验证结果
        assertTrue(success, "文件上传应该成功");
        assertTrue(ragService.isKnowledgeBaseInitialized(), "知识库应该包含文档");

        System.out.println("✓ 测试6通过: 用户文件成功上传到知识库");
    }

    @Test
    @DisplayName("测试7: 清空知识库")
    void testClearKnowledgeBase() {
        // 初始化并加载知识库
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 验证知识库已初始化
        assertTrue(ragService.isKnowledgeBaseInitialized(), "知识库应该已初始化");

        // 清空知识库
        ragService.clearKnowledgeBase();

        // 验证知识库已清空
        assertFalse(ragService.isKnowledgeBaseInitialized(), "知识库应该已清空");

        String stats = ragService.getKnowledgeBaseStats();
        assertTrue(stats.contains("0") || stats.contains("RAG 系统未初始化"),
                  "统计信息应显示0个文档");

        System.out.println("✓ 测试7通过: 知识库成功清空");
    }

    @Test
    @DisplayName("测试8: 获取知识库统计信息")
    void testGetKnowledgeBaseStats() {
        // 初始化 RagService
        ragService.initialize();

        // 未加载文档前
        String statsBefore = ragService.getKnowledgeBaseStats();
        System.out.println("加载前统计: " + statsBefore);

        // 加载文档
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        testFiles.add(testMdFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 加载后统计
        String statsAfter = ragService.getKnowledgeBaseStats();
        System.out.println("加载后统计: " + statsAfter);

        assertNotNull(statsAfter, "统计信息不应为空");
        assertTrue(statsAfter.contains("知识库文档数量"), "应该包含文档数量信息");

        System.out.println("✓ 测试8通过: 统计信息功能正常");
    }

    @Test
    @DisplayName("测试9: 空查询处理")
    void testProcessEmptyQuery() {
        // 初始化
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 执行空查询
        String emptyQuestion = "";
        Answer answer = ragService.processQuery(emptyQuestion, null);

        // 验证答案
        assertNotNull(answer, "答案不应为空");
        assertNotNull(answer.getContent(), "答案内容不应为空");

        System.out.println("空查询答案: " + answer.getContent());

        System.out.println("✓ 测试9通过: 空查询处理正常");
    }

    @Test
    @DisplayName("测试10: 多次查询性能")
    void testMultipleQueriesPerformance() {
        // 初始化并加载知识库
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        testFiles.add(testMdFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        // 执行多次查询
        String[] questions = {
            "什么是Java？",
            "Java有哪些特点？",
            "什么是面向对象？",
            "什么是继承？",
            "什么是多态？"
        };

        long startTime = System.currentTimeMillis();
        int successCount = 0;

        for (String question : questions) {
            Answer answer = ragService.processQuery(question, null);
            if (answer != null && answer.getContent() != null) {
                successCount++;
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("执行 " + questions.length + " 次查询");
        System.out.println("成功: " + successCount + " 次");
        System.out.println("总耗时: " + totalTime + " ms");
        System.out.println("平均耗时: " + (totalTime / questions.length) + " ms/查询");

        assertEquals(questions.length, successCount, "所有查询都应该成功");

        System.out.println("✓ 测试10通过: 多次查询性能测试完成");
    }

    @Test
    @DisplayName("测试11: 未初始化时的行为")
    void testBehaviorBeforeInitialization() {
        // 创建未初始化的 RagService
        RagService uninitializedService = new RagService(mockProject);

        // 测试统计信息
        String stats = uninitializedService.getKnowledgeBaseStats();
        assertTrue(stats.contains("RAG 系统未初始化") || stats.contains("0"),
                  "未初始化时应该提示系统未初始化");

        // 测试查询（应该触发自动初始化）
        Answer answer = uninitializedService.processQuery("测试问题", null);
        assertNotNull(answer, "即使未初始化，查询也应该返回答案");

        // 清理
        uninitializedService.close();

        System.out.println("✓ 测试11通过: 未初始化状态处理正常");
    }

    @Test
    @DisplayName("测试12: 知识库持久化")
    void testKnowledgeBasePersistence() {
        // 第一次初始化并加载
        ragService.initialize();
        List<File> testFiles = new ArrayList<>();
        testFiles.add(testTxtFile);
        ragService.initializeKnowledgeBase(testFiles, DocumentChunk.SourceType.STATIC);

        String stats1 = ragService.getKnowledgeBaseStats();
        System.out.println("第一次加载: " + stats1);

        // 关闭服务
        ragService.close();

        // 重新创建服务（使用相同的路径）
        RagService newService = new RagService(mockProject);
        newService.initialize();

        // 检查数据是否持久化
        String stats2 = newService.getKnowledgeBaseStats();
        System.out.println("重新加载: " + stats2);

        assertTrue(newService.isKnowledgeBaseInitialized(), "知识库数据应该持久化");

        // 清理
        newService.close();

        System.out.println("✓ 测试12通过: 知识库持久化功能正常");
    }
}

