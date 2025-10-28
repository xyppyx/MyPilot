package com.javaee.mypilot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.javaee.mypilot.service.RagService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MyPilot 项目启动组件
 * 在项目打开时自动初始化 RAG 服务
 */
public class MyPilotProjectComponent implements ProjectActivity {

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // 在后台线程中初始化 RAG 服务，避免阻塞项目启动
        new Thread(() -> {
            try {
                System.out.println("MyPilot: 开始初始化 RAG 服务...");
                RagService ragService = RagService.getInstance(project);
                ragService.initialize();
                System.out.println("MyPilot: RAG 服务初始化完成");
            } catch (Exception e) {
                System.err.println("MyPilot: RAG 服务初始化失败: " + e.getMessage());
                e.printStackTrace();
            }
        }, "MyPilot-RAG-Init").start();

        return Unit.INSTANCE;
    }
}

