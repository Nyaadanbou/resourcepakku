@file:JvmName("InternalExecutors")

package cc.mewcraft.resourcepakku.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 一个使用虚拟线程执行任务的 [ExecutorService] 实例.
 */
val VIRTUAL_THREAD_EXECUTOR: ExecutorService = Executors.newThreadPerTaskExecutor(
    ThreadFactoryBuilder()
        .setThreadFactory(Thread.ofVirtual().factory())
        .setNameFormat("resrcpack-virtual-thread-%d")
        .build()
)