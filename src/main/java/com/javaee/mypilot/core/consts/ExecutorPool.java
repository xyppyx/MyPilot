package com.javaee.mypilot.core.consts;

/**
 * 线程池常量
 */
public class ExecutorPool {

    // 获取可用的 CPU 核心数
    public static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    // CPU 线程池参数： 线程数应该与 CPU 核心数相匹配
    public static final int CPU_CORE_POOL_SIZE = CPU_CORES + 1;

    // CPU 线程池最大线程数
    public static final int CPU_MAX_POOL_SIZE = CPU_CORE_POOL_SIZE;

    // IO 线程池参数： 线程数可以是 CPU 核心数的两倍，以处理更多的 I/O 任务
    public static final int IO_CORE_POOL_SIZE = CPU_CORES * 2;

    // IO 线程池最大线程数
    public static final int IO_MAX_POOL_SIZE = 200;
}
