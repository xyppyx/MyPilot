package com.javaee.mypilot.infra;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.core.consts.ExecutorPool;

import java.util.concurrent.*;

/**
 * 线程池管理器
 * 提供 IO 密集型和 CPU 密集型任务的线程池
 */
@Service(Service.Level.PROJECT)
public final class AppExecutors implements Disposable {

    /**
     * IO 密集型任务线程池
     */
    private final ExecutorService ioExecutor = new ThreadPoolExecutor(
            ExecutorPool.IO_CORE_POOL_SIZE,
            ExecutorPool.IO_MAX_POOL_SIZE,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            r -> new Thread(r, "my-cpu-pool-thread-" + r.hashCode())
    );

    /**
     * CPU 密集型任务线程池
     */
    private final ExecutorService cpuExecutor = new ThreadPoolExecutor(
            ExecutorPool.CPU_CORE_POOL_SIZE,
            ExecutorPool.CPU_MAX_POOL_SIZE,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            r -> new Thread(r, "my-io-pool-thread-" + r.hashCode())
    );

    public static AppExecutors getInstance(Project project) {
        return project.getService(AppExecutors.class);
    }

    public Executor getIoExecutor() { return ioExecutor; }

    public Executor getCpuExecutor() { return cpuExecutor; }

    /**
     * 释放资源，关闭线程池
     */
    @Override
    public void dispose() {
        ioExecutor.shutdown();
        cpuExecutor.shutdown();
    }
}