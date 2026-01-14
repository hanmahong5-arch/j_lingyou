package red.jiuzhou.ui.error.engine;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import red.jiuzhou.config.validation.ConfigValidationService;
import red.jiuzhou.ui.error.navigation.ErrorNavigationService;
import red.jiuzhou.ui.error.navigation.ProblemsPanel;
import red.jiuzhou.ui.error.structured.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 错误诊断引擎 - 统一的错误处理入口
 *
 * <p>世界级错误处理系统的核心组件，提供：
 * <ul>
 *   <li>异常自动分析和分类</li>
 *   <li>结构化错误信息生成</li>
 *   <li>修复建议自动推荐</li>
 *   <li>错误持久化和日志</li>
 *   <li>AI友好的错误导出</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Service
public class ErrorDiagnosticsEngine {

    private static final Logger log = LoggerFactory.getLogger(ErrorDiagnosticsEngine.class);

    private static final String LOG_DIR = "logs";
    private static final String ERROR_LOG_FILE = "structured-errors.jsonl";

    @Autowired(required = false)
    private ConfigValidationService configValidation;

    @Autowired(required = false)
    private ErrorNavigationService navigation;

    // 问题面板引用（由UI层设置）
    private ProblemsPanel problemsPanel;

    // 错误历史记录
    private final ConcurrentLinkedQueue<StructuredError> errorHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY_SIZE = 1000;

    // 错误监听器
    private final List<Consumer<StructuredError>> listeners = new CopyOnWriteArrayList<>();

    // 统计
    private volatile int totalErrors = 0;
    private volatile int totalWarnings = 0;
    private volatile Instant lastErrorTime;

    // ==================== 报告方法 ====================

    /**
     * 静态分析方法（无需Spring依赖）
     *
     * 用于在非Spring上下文中快速分析异常，例如批量导入失败记录
     *
     * @param throwable 异常对象
     * @param component 组件名
     * @param contextMap 上下文信息
     * @return 结构化错误对象
     */
    public static StructuredError analyze(
            Throwable throwable,
            String component,
            Map<String, String> contextMap) {

        if (throwable == null) {
            throw new IllegalArgumentException("throwable cannot be null");
        }

        ErrorCodes code = ErrorCodes.fromException(throwable);
        ErrorCategory category = ErrorCategory.fromException(throwable);

        StructuredError.Builder builder = StructuredError.builder()
            .errorCode(code.getCode())
            .level(code.getLevel())
            .category(category)
            .title(code.getTitle())
            .message(throwable.getMessage())
            .component(component != null ? component : "Unknown")
            .stackTrace(getStaticStackTraceString(throwable))
            .cause(throwable)
            .documentationUrl(code.getDocUrl());

        // 添加上下文信息
        if (contextMap != null) {
            contextMap.forEach(builder::addContext);
        }

        builder.addContext("exception_type", throwable.getClass().getName());

        return builder.build();
    }

    /**
     * 获取异常堆栈的字符串表示（静态版本）
     */
    private static String getStaticStackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 报告异常
     */
    public void report(Throwable throwable, String context, String component) {
        if (throwable == null) return;

        StructuredError error = analyzeAndConvert(throwable, context, component);
        processError(error);
    }

    /**
     * 报告结构化错误
     */
    public void report(StructuredError error) {
        if (error == null) return;
        processError(error);
    }

    /**
     * 报告配置错误
     */
    public void reportConfigError(String configKey, String message, ErrorLevel level) {
        StructuredError error = StructuredError.builder()
            .errorCode(level == ErrorLevel.ERROR ? "CFG-0001" : "CFG-0003")
            .level(level)
            .category(ErrorCategory.CONFIGURATION)
            .title("配置问题: " + configKey)
            .message(message)
            .location(ErrorLocation.fromConfigKey("src/main/resources/application.yml", configKey))
            .addContext("config_key", configKey)
            .component("ConfigValidation")
            .addSuggestion(FixSuggestion.navigateToKey(configKey))
            .build();

        processError(error);
    }

    /**
     * 报告数据库错误
     */
    public void reportDatabaseError(Throwable t, String sql) {
        StructuredError error = StructuredError.fromException(t, "Database")
            .addContext("sql", sql)
            .addSuggestion(FixSuggestion.manual(
                "检查数据库连接",
                "确认MySQL服务正在运行，数据库连接配置正确"))
            .build();

        processError(error);
    }

    /**
     * 报告AI服务错误
     */
    public void reportAiServiceError(Throwable t, String modelName) {
        ErrorCodes code = t.getMessage() != null && t.getMessage().contains("api")
            ? ErrorCodes.AI_API_KEY_INVALID
            : ErrorCodes.AI_SERVICE_UNAVAILABLE;

        StructuredError error = StructuredError.fromCode(code)
            .message(t.getMessage())
            .cause(t)
            .addContext("model", modelName)
            .component("AIService")
            .addSuggestion(FixSuggestion.navigateToKey("ai." + modelName + ".apikey"))
            .addSuggestion(FixSuggestion.viewDocumentation(
                "获取API密钥",
                "https://dashscope.console.aliyun.com/"))
            .build();

        processError(error);
    }

    // ==================== 处理流程 ====================

    /**
     * 处理错误
     */
    private void processError(StructuredError error) {
        // 1. 更新统计
        updateStats(error);

        // 2. 记录日志
        logError(error);

        // 3. 添加到历史
        addToHistory(error);

        // 4. 通知监听器
        notifyListeners(error);

        // 5. 更新问题面板
        if (problemsPanel != null) {
            problemsPanel.addProblem(error);
        }

        // 6. 持久化到日志文件
        persistError(error);
    }

    /**
     * 分析异常并转换为结构化错误
     */
    private StructuredError analyzeAndConvert(Throwable t, String context, String component) {
        ErrorCodes code = ErrorCodes.fromException(t);
        ErrorCategory category = ErrorCategory.fromException(t);
        ErrorLocation location = inferLocation(t);
        List<FixSuggestion> suggestions = generateSuggestions(t, code);

        return StructuredError.builder()
            .errorCode(code.getCode())
            .level(code.getLevel())
            .category(category)
            .title(code.getTitle())
            .message(t.getMessage())
            .location(location)
            .addContext("context", context)
            .addContext("component", component)
            .addContext("exception_type", t.getClass().getName())
            .stackTrace(getStackTraceString(t))
            .cause(t)
            .suggestions(suggestions)
            .component(component)
            .documentationUrl(code.getDocUrl())
            .build();
    }

    /**
     * 推断错误位置
     */
    private ErrorLocation inferLocation(Throwable t) {
        StackTraceElement[] stackTrace = t.getStackTrace();

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();

            // 优先匹配项目内的类
            if (className.startsWith("red.jiuzhou")) {
                String fileName = element.getFileName();
                int lineNumber = element.getLineNumber();

                // 尝试解析源文件路径
                String filePath = resolveSourcePath(className, fileName);

                return ErrorLocation.line(filePath, lineNumber);
            }
        }

        return null;
    }

    /**
     * 解析源代码路径
     */
    private String resolveSourcePath(String className, String fileName) {
        if (fileName == null) return null;

        // 将类名转换为路径
        String packagePath = className.replace('.', File.separatorChar);
        int lastDot = packagePath.lastIndexOf(File.separatorChar);
        if (lastDot > 0) {
            packagePath = packagePath.substring(0, lastDot);
        }

        return "src/main/java/" + packagePath + "/" + fileName;
    }

    /**
     * 生成修复建议
     */
    private List<FixSuggestion> generateSuggestions(Throwable t, ErrorCodes code) {
        List<FixSuggestion> suggestions = new ArrayList<>();

        switch (code) {
            case CFG_AI_NOT_CONFIGURED:
                suggestions.add(FixSuggestion.navigateToKey("ai.qwen.apikey"));
                suggestions.add(FixSuggestion.viewDocumentation(
                    "查看AI配置文档",
                    "https://dashscope.console.aliyun.com/"));
                break;

            case CFG_DB_CONNECTION:
                suggestions.add(FixSuggestion.navigateToKey("spring.datasource.url"));
                suggestions.add(FixSuggestion.manual(
                    "检查MySQL服务",
                    "运行 'sc query mysql' 检查MySQL服务是否正在运行"));
                break;

            case DB_CONNECTION_TIMEOUT:
            case DB_CONNECTION_LOST:
                suggestions.add(FixSuggestion.manual(
                    "重启数据库连接",
                    "重启应用或检查网络连接"));
                break;

            case AI_API_KEY_INVALID:
                suggestions.add(FixSuggestion.navigateToKey("ai.qwen.apikey"));
                suggestions.add(FixSuggestion.viewDocumentation(
                    "获取新的API密钥",
                    "https://dashscope.console.aliyun.com/"));
                break;

            case IO_FILE_NOT_FOUND:
                String path = t.getMessage();
                if (path != null && path.contains("application.yml")) {
                    suggestions.add(FixSuggestion.manual(
                        "复制配置模板",
                        "运行: copy application.yml.example application.yml"));
                }
                break;

            default:
                // 通用建议
                suggestions.add(FixSuggestion.manual(
                    "查看日志详情",
                    "检查 logs/app.log 获取更多信息"));
                break;
        }

        return suggestions;
    }

    // ==================== 辅助方法 ====================

    private void updateStats(StructuredError error) {
        if (error.level() == ErrorLevel.ERROR || error.level() == ErrorLevel.FATAL) {
            totalErrors++;
        } else if (error.level() == ErrorLevel.WARNING) {
            totalWarnings++;
        }
        lastErrorTime = Instant.now();
    }

    private void logError(StructuredError error) {
        String humanReadable = error.toHumanReadable();

        switch (error.level()) {
            case FATAL, ERROR -> log.error("\n{}", humanReadable);
            case WARNING -> log.warn("\n{}", humanReadable);
            default -> log.info("\n{}", humanReadable);
        }
    }

    private void addToHistory(StructuredError error) {
        errorHistory.add(error);

        // 限制历史大小
        while (errorHistory.size() > MAX_HISTORY_SIZE) {
            errorHistory.poll();
        }
    }

    private void notifyListeners(StructuredError error) {
        for (Consumer<StructuredError> listener : listeners) {
            try {
                listener.accept(error);
            } catch (Exception e) {
                log.debug("通知监听器失败", e);
            }
        }
    }

    private void persistError(StructuredError error) {
        try {
            Path logDir = Path.of(LOG_DIR);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            // 按日期分文件
            String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path logFile = logDir.resolve("errors-" + dateStr + ".jsonl");

            String jsonLine = error.toJson().replace("\n", " ") + "\n";

            Files.writeString(logFile, jsonLine,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

        } catch (IOException e) {
            log.debug("持久化错误日志失败", e);
        }
    }

    private String getStackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ==================== 公共API ====================

    /**
     * 设置问题面板
     */
    public void setProblemsPanel(ProblemsPanel panel) {
        this.problemsPanel = panel;
    }

    /**
     * 添加错误监听器
     */
    public void addListener(Consumer<StructuredError> listener) {
        listeners.add(listener);
    }

    /**
     * 移除错误监听器
     */
    public void removeListener(Consumer<StructuredError> listener) {
        listeners.remove(listener);
    }

    /**
     * 获取错误历史
     */
    public List<StructuredError> getErrorHistory() {
        return new ArrayList<>(errorHistory);
    }

    /**
     * 获取最近的错误
     */
    public List<StructuredError> getRecentErrors(int count) {
        List<StructuredError> all = new ArrayList<>(errorHistory);
        int start = Math.max(0, all.size() - count);
        return all.subList(start, all.size());
    }

    /**
     * 导出错误给AI分析
     */
    public String exportForAI(List<StructuredError> errors) {
        return errors.stream()
            .map(StructuredError::toJson)
            .collect(Collectors.joining("\n"));
    }

    /**
     * 导出所有错误给AI分析
     */
    public String exportAllForAI() {
        return exportForAI(getErrorHistory());
    }

    /**
     * 清空错误历史
     */
    public void clearHistory() {
        errorHistory.clear();
        totalErrors = 0;
        totalWarnings = 0;
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "totalErrors", totalErrors,
            "totalWarnings", totalWarnings,
            "historySize", errorHistory.size(),
            "lastErrorTime", lastErrorTime != null ? lastErrorTime.toString() : "N/A"
        );
    }

    /**
     * 执行配置验证
     */
    public List<StructuredError> validateConfiguration() {
        if (configValidation != null) {
            List<StructuredError> errors = configValidation.validateAll();
            errors.forEach(this::processError);
            return errors;
        }
        return Collections.emptyList();
    }
}
