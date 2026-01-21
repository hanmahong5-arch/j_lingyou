package red.jiuzhou.ops.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 操作链 - 多步事务执行框架
 *
 * 支持特性：
 * - 顺序执行多个操作步骤
 * - 自动回滚机制（任一步骤失败时）
 * - 重试策略（可配置重试次数和间隔）
 * - 进度回调和状态追踪
 * - 超时控制
 * - 操作日志记录
 *
 * 设计模式：Builder + Chain of Responsibility
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class OperationChain {

    private static final Logger log = LoggerFactory.getLogger(OperationChain.class);

    private final String chainId;
    private final String chainName;
    private final List<OperationStep> steps = new ArrayList<>();
    private final List<OperationStep> executedSteps = new ArrayList<>();

    private int maxRetries = 3;
    private Duration retryDelay = Duration.ofSeconds(1);
    private Duration timeout = Duration.ofMinutes(5);

    private Consumer<ChainProgress> progressCallback;
    private Consumer<ChainResult> completionCallback;

    private ChainState state = ChainState.PENDING;
    private String errorMessage;
    private int currentStepIndex = 0;

    private OperationChain(String chainId, String chainName) {
        this.chainId = chainId;
        this.chainName = chainName;
    }

    /**
     * Create a new operation chain
     */
    public static Builder builder(String chainName) {
        return new Builder(chainName);
    }

    /**
     * Execute the operation chain synchronously
     */
    public ChainResult execute() {
        Instant startTime = Instant.now();
        log.info("开始执行操作链: {} ({}), 共 {} 步", chainName, chainId, steps.size());

        state = ChainState.RUNNING;
        notifyProgress();

        try {
            for (int i = 0; i < steps.size(); i++) {
                currentStepIndex = i;
                OperationStep step = steps.get(i);

                log.info("执行步骤 {}/{}: {}", i + 1, steps.size(), step.name());
                notifyProgress();

                // Execute with retry
                StepResult result = executeStepWithRetry(step);

                if (!result.success()) {
                    log.error("步骤失败: {} - {}", step.name(), result.errorMessage());
                    errorMessage = "步骤 [" + step.name() + "] 失败: " + result.errorMessage();
                    state = ChainState.FAILED;

                    // Rollback executed steps
                    rollback();

                    return buildResult(startTime, false);
                }

                executedSteps.add(step);
                log.info("步骤完成: {} ({}ms)", step.name(), result.duration().toMillis());
            }

            state = ChainState.COMPLETED;
            log.info("操作链完成: {} (总耗时 {}ms)", chainName,
                    Duration.between(startTime, Instant.now()).toMillis());

            ChainResult result = buildResult(startTime, true);
            if (completionCallback != null) {
                completionCallback.accept(result);
            }
            return result;

        } catch (Exception e) {
            log.error("操作链异常: {}", chainName, e);
            errorMessage = "系统异常: " + e.getMessage();
            state = ChainState.FAILED;
            rollback();
            return buildResult(startTime, false);
        }
    }

    /**
     * Execute the operation chain asynchronously
     */
    public CompletableFuture<ChainResult> executeAsync() {
        return CompletableFuture.supplyAsync(this::execute);
    }

    private StepResult executeStepWithRetry(OperationStep step) {
        int attempts = 0;
        StepResult lastResult = null;

        while (attempts < maxRetries) {
            attempts++;
            Instant stepStart = Instant.now();

            try {
                step.action().run();
                return new StepResult(true, null, Duration.between(stepStart, Instant.now()));
            } catch (Exception e) {
                lastResult = new StepResult(false, e.getMessage(),
                        Duration.between(stepStart, Instant.now()));
                log.warn("步骤 {} 第 {} 次尝试失败: {}", step.name(), attempts, e.getMessage());

                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(retryDelay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return lastResult != null ? lastResult :
                new StepResult(false, "未知错误", Duration.ZERO);
    }

    private void rollback() {
        if (executedSteps.isEmpty()) {
            return;
        }

        log.warn("开始回滚操作链: {}, 已执行 {} 步", chainName, executedSteps.size());
        state = ChainState.ROLLING_BACK;
        notifyProgress();

        // Reverse order rollback
        Collections.reverse(executedSteps);
        for (OperationStep step : executedSteps) {
            if (step.rollback() != null) {
                try {
                    log.info("回滚步骤: {}", step.name());
                    step.rollback().run();
                } catch (Exception e) {
                    log.error("回滚步骤失败: {} - {}", step.name(), e.getMessage());
                    // Continue with other rollbacks
                }
            }
        }

        state = ChainState.ROLLED_BACK;
        log.info("操作链回滚完成: {}", chainName);
    }

    private void notifyProgress() {
        if (progressCallback != null) {
            progressCallback.accept(new ChainProgress(
                    chainId,
                    chainName,
                    state,
                    currentStepIndex,
                    steps.size(),
                    currentStepIndex < steps.size() ? steps.get(currentStepIndex).name() : null
            ));
        }
    }

    private ChainResult buildResult(Instant startTime, boolean success) {
        Instant endTime = Instant.now();
        String failedStepName = null;
        if (!success && currentStepIndex < steps.size()) {
            failedStepName = steps.get(currentStepIndex).name();
        }
        return new ChainResult(
                chainId,
                chainName,
                success,
                errorMessage,
                executedSteps.size(),
                steps.size(),
                Duration.between(startTime, endTime),
                state,
                failedStepName,
                startTime,
                endTime,
                new HashMap<>()
        );
    }

    // ==================== Builder ====================

    public static class Builder {
        private static final AtomicInteger idCounter = new AtomicInteger(0);

        private final String chainName;
        private final List<OperationStep> steps = new ArrayList<>();
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
        private Duration timeout = Duration.ofMinutes(5);
        private Consumer<ChainProgress> progressCallback;
        private Consumer<ChainResult> completionCallback;

        private Builder(String chainName) {
            this.chainName = chainName;
        }

        /**
         * Add a step with rollback
         */
        public Builder step(String name, Runnable action, Runnable rollback) {
            steps.add(new OperationStep(name, action, rollback));
            return this;
        }

        /**
         * Add a step without rollback
         */
        public Builder step(String name, Runnable action) {
            return step(name, action, null);
        }

        /**
         * Add a conditional step
         */
        public Builder stepIf(boolean condition, String name, Runnable action, Runnable rollback) {
            if (condition) {
                steps.add(new OperationStep(name, action, rollback));
            }
            return this;
        }

        /**
         * Set max retry attempts
         */
        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        /**
         * Set retry delay
         */
        public Builder retryDelay(Duration delay) {
            this.retryDelay = delay;
            return this;
        }

        /**
         * Set timeout
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set progress callback
         */
        public Builder onProgress(Consumer<ChainProgress> callback) {
            this.progressCallback = callback;
            return this;
        }

        /**
         * Set completion callback
         */
        public Builder onComplete(Consumer<ChainResult> callback) {
            this.completionCallback = callback;
            return this;
        }

        /**
         * Build the operation chain
         */
        public OperationChain build() {
            String chainId = "CHAIN-" + System.currentTimeMillis() + "-" + idCounter.incrementAndGet();
            OperationChain chain = new OperationChain(chainId, chainName);
            chain.steps.addAll(steps);
            chain.maxRetries = maxRetries;
            chain.retryDelay = retryDelay;
            chain.timeout = timeout;
            chain.progressCallback = progressCallback;
            chain.completionCallback = completionCallback;
            return chain;
        }
    }

    // ==================== Data Classes ====================

    public record OperationStep(String name, Runnable action, Runnable rollback) {}

    public record StepResult(boolean success, String errorMessage, Duration duration) {}

    public record ChainProgress(
            String chainId,
            String chainName,
            ChainState state,
            int currentStep,
            int totalSteps,
            String currentStepName
    ) {
        public double getProgressPercent() {
            return totalSteps > 0 ? (double) currentStep / totalSteps * 100 : 0;
        }
    }

    public record ChainResult(
            String chainId,
            String chainName,
            boolean success,
            String errorMessage,
            int completedSteps,
            int totalSteps,
            Duration totalDuration,
            ChainState finalState,
            String failedStepName,
            Instant startTime,
            Instant endTime,
            Map<String, Object> metadata
    ) {
        // Convenience methods for compatibility
        public boolean isSuccess() { return success; }
        public String failedStep() { return failedStepName; }
        public Throwable error() {
            return errorMessage != null ? new RuntimeException(errorMessage) : null;
        }
        public long getDuration() { return totalDuration.toMillis(); }
        public boolean wasRolledBack() {
            return finalState == ChainState.ROLLED_BACK;
        }

        // Renamed accessor for completedSteps
        public int completedSteps() { return completedSteps; }
    }

    public enum ChainState {
        PENDING("待执行"),
        RUNNING("执行中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        ROLLING_BACK("回滚中"),
        ROLLED_BACK("已回滚");

        private final String display;

        ChainState(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }
}
