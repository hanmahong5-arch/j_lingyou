package red.jiuzhou.ui.features;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.concurrent.Task;

/**
 * 特性任务执行器（虚拟线程版本）
 *
 * <p>使用 Java 21+ 虚拟线程实现轻量级并发任务执行。
 * 虚拟线程由 JVM 管理，无需手动配置线程池大小，自动伸缩。
 */
public final class FeatureTaskExecutor {

    private static final FeatureTaskExecutor INSTANCE = new FeatureTaskExecutor();

    private final ExecutorService executor;

    private FeatureTaskExecutor() {
        // Java 21+ 虚拟线程：轻量级、自动伸缩、无需配置线程池大小
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 提交任务到虚拟线程执行
     *
     * @param task       JavaFX Task
     * @param threadName 线程名称（用于日志追踪）
     */
    public static void run(Task<?> task, String threadName) {
        Objects.requireNonNull(task, "task must not be null");
        INSTANCE.executor.submit(() -> {
            Thread current = Thread.currentThread();
            String originalName = current.getName();
            try {
                if (threadName != null && !threadName.isBlank()) {
                    current.setName(threadName);
                }
                task.run();
            } finally {
                current.setName(originalName);
            }
        });
    }

    /**
     * 关闭执行器
     */
    public static void shutdown() {
        INSTANCE.executor.shutdownNow();
    }
}
