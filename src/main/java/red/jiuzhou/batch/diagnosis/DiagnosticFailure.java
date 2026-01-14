package red.jiuzhou.batch.diagnosis;

import red.jiuzhou.ui.error.structured.ErrorCategory;
import red.jiuzhou.ui.error.structured.ErrorLevel;
import red.jiuzhou.ui.error.structured.StructuredError;
import red.jiuzhou.ui.error.engine.ErrorDiagnosticsEngine;

import java.io.File;
import java.time.Instant;
import java.util.*;

/**
 * Diagnostic Failure Record
 *
 * 增强版失败记录，替换简单的 FailedFile(path, error) 元组
 * 集成 StructuredError 实现完整的诊断能力
 *
 * 核心特性：
 * - 完整异常上下文（不只是错误字符串）
 * - 结构化错误模型（错误分类、修复建议）
 * - AI 诊断结果缓存
 * - 重试追踪和状态管理
 *
 * @author Claude AI
 * @date 2026-01-15
 */
public record DiagnosticFailure(
    // ========== 文件信息 ==========
    String filePath,          // 完整文件路径
    String fileName,          // 文件名（含扩展名）
    String tableName,         // 目标表名
    File file,                // File 对象（便于操作）

    // ========== 错误诊断核心 ==========
    StructuredError structuredError,  // 结构化错误（核心！）
    Throwable originalException,      // 原始异常（完整堆栈）

    // ========== 导入上下文 ==========
    String importPhase,               // 失败阶段（DDL生成/建表/数据导入/AI处理）
    Map<String, Object> context,      // 上下文信息（XML片段、表结构等）

    // ========== AI 诊断 ==========
    String aiDiagnosis,               // AI 诊断结果
    List<String> aiSuggestions,       // AI 修复建议列表
    Instant aiDiagnosisTime,          // AI 诊断时间

    // ========== 状态管理 ==========
    Instant failedTime,               // 失败时间
    boolean retryable,                // 是否可重试
    int retryCount                    // 重试次数
) {

    // ==================== 构造函数 ====================

    /**
     * Compact constructor with validation
     */
    public DiagnosticFailure {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(structuredError, "structuredError cannot be null");

        // 确保不可变集合
        context = context != null ? Map.copyOf(context) : Map.of();
        aiSuggestions = aiSuggestions != null ? List.copyOf(aiSuggestions) : List.of();
    }

    // ==================== 工厂方法 ====================

    /**
     * 从异常创建 DiagnosticFailure
     *
     * 这是主要的创建方式，自动集成 ErrorDiagnosticsEngine
     *
     * @param file 失败的 XML 文件
     * @param exception 原始异常
     * @param phase 失败阶段（如 "DDL生成", "数据导入"）
     * @param context 上下文信息（可选）
     * @return DiagnosticFailure 实例
     */
    public static DiagnosticFailure fromException(
            File file,
            Throwable exception,
            String phase,
            Map<String, Object> context) {

        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(exception, "exception cannot be null");

        // 提取文件信息
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        String tableName = extractTableName(fileName);

        // 使用 ErrorDiagnosticsEngine 生成结构化错误
        StructuredError structuredError = ErrorDiagnosticsEngine.analyze(
            exception,
            "BatchImportService",
            Map.of(
                "file", filePath,
                "table", tableName,
                "phase", phase != null ? phase : "未知阶段"
            )
        );

        // 判断是否可重试（基于错误类别）
        boolean retryable = isRetryable(structuredError.category(), exception);

        return new DiagnosticFailure(
            filePath,
            fileName,
            tableName,
            file,
            structuredError,
            exception,
            phase != null ? phase : "未知阶段",
            context != null ? context : Map.of(),
            null,  // 初始无 AI 诊断
            List.of(),
            null,
            Instant.now(),
            retryable,
            0
        );
    }

    /**
     * 简化版工厂方法（无上下文）
     */
    public static DiagnosticFailure fromException(File file, Throwable exception, String phase) {
        return fromException(file, exception, phase, null);
    }

    /**
     * 从 BatchXmlImporter.FailedFile 转换（兼容旧版）
     *
     * @deprecated 仅用于渐进式迁移，新代码应使用 fromException
     */
    @Deprecated(since = "2026-01-15", forRemoval = true)
    public static DiagnosticFailure fromLegacyFailedFile(String filePath, String errorMessage) {
        File file = new File(filePath);

        // 构造简单的异常
        RuntimeException exception = new RuntimeException(errorMessage);

        // 生成基础的 StructuredError
        StructuredError structuredError = StructuredError.builder()
            .errorCode("LEGACY-0001")
            .level(ErrorLevel.ERROR)
            .category(ErrorCategory.UNKNOWN)
            .title("导入失败（遗留格式）")
            .message(errorMessage)
            .component("LegacyMigration")
            .build();

        return new DiagnosticFailure(
            filePath,
            file.getName(),
            extractTableName(file.getName()),
            file,
            structuredError,
            exception,
            "未知阶段",
            Map.of(),
            null,
            List.of(),
            null,
            Instant.now(),
            false,  // 遗留格式不支持重试
            0
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取错误摘要（用于列表显示）
     */
    public String getErrorSummary() {
        return structuredError.toSummary();
    }

    /**
     * 获取人类可读的错误详情（Rust 风格）
     */
    public String getErrorDetail() {
        return structuredError.toHumanReadable();
    }

    /**
     * 检查是否已有 AI 诊断
     */
    public boolean hasAiDiagnosis() {
        return aiDiagnosis != null && !aiDiagnosis.isEmpty();
    }

    /**
     * 创建带 AI 诊断的新实例（不可变更新）
     */
    public DiagnosticFailure withAiDiagnosis(String diagnosis, List<String> suggestions) {
        return new DiagnosticFailure(
            filePath, fileName, tableName, file,
            structuredError, originalException,
            importPhase, context,
            diagnosis,
            suggestions != null ? suggestions : List.of(),
            Instant.now(),
            failedTime, retryable, retryCount
        );
    }

    /**
     * 创建重试后的新实例（增加重试计数）
     */
    public DiagnosticFailure withRetry() {
        return new DiagnosticFailure(
            filePath, fileName, tableName, file,
            structuredError, originalException,
            importPhase, context,
            aiDiagnosis, aiSuggestions, aiDiagnosisTime,
            failedTime, retryable, retryCount + 1
        );
    }

    /**
     * 创建标记为不可重试的新实例
     */
    public DiagnosticFailure withRetryable(boolean retryable) {
        return new DiagnosticFailure(
            filePath, fileName, tableName, file,
            structuredError, originalException,
            importPhase, context,
            aiDiagnosis, aiSuggestions, aiDiagnosisTime,
            failedTime, retryable, retryCount
        );
    }

    /**
     * 获取错误类别
     */
    public ErrorCategory getCategory() {
        return structuredError.category();
    }

    /**
     * 获取错误级别
     */
    public ErrorLevel getLevel() {
        return structuredError.level();
    }

    /**
     * 获取错误码
     */
    public String getErrorCode() {
        return structuredError.errorCode();
    }

    /**
     * 是否为高优先级错误（ERROR 或 FATAL）
     */
    public boolean isHighPriority() {
        return structuredError.level() == ErrorLevel.ERROR ||
               structuredError.level() == ErrorLevel.FATAL;
    }

    /**
     * 获取建议的修复操作数量
     */
    public int getSuggestionsCount() {
        int count = structuredError.suggestions() != null ? structuredError.suggestions().size() : 0;
        return count + aiSuggestions.size();
    }

    /**
     * 检查是否达到最大重试次数
     */
    public boolean hasExceededRetryLimit() {
        return retryCount >= 3;  // 最多重试 3 次
    }

    /**
     * 导出为 JSON（用于报告生成）
     */
    public String toJson() {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("file_path", filePath);
        json.put("file_name", fileName);
        json.put("table_name", tableName);
        json.put("import_phase", importPhase);
        json.put("failed_time", failedTime.toString());
        json.put("retryable", retryable);
        json.put("retry_count", retryCount);

        // 嵌入结构化错误的 JSON
        json.put("structured_error", structuredError.toJson());

        // AI 诊断信息
        if (hasAiDiagnosis()) {
            json.put("ai_diagnosis", aiDiagnosis);
            json.put("ai_suggestions", aiSuggestions);
            json.put("ai_diagnosis_time", aiDiagnosisTime.toString());
        }

        return com.alibaba.fastjson2.JSON.toJSONString(json,
            com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从文件名提取表名（去掉 .xml 扩展名）
     */
    private static String extractTableName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }

        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 判断错误是否可重试
     *
     * 规则：
     * - 配置错误：可重试（修复配置后可重新尝试）
     * - 数据库连接错误：可重试（网络波动）
     * - 验证错误：不可重试（数据冲突需要用户选择策略）
     * - 文件 IO 错误：不可重试（文件损坏或权限问题）
     * - 网络错误：可重试（网络波动）
     * - 系统错误：视情况而定
     */
    private static boolean isRetryable(ErrorCategory category, Throwable exception) {
        if (category == null) {
            return false;
        }

        return switch (category) {
            case CONFIGURATION -> true;     // 配置修复后可重试
            case DATABASE -> {
                // 检查是否为连接错误（而非数据冲突）
                String message = exception.getMessage();
                if (message == null) {
                    yield true;  // 无消息时默认可重试
                }
                // 如果是重复键错误，不可重试（需要用户选择策略）
                boolean isDuplicateKey = message.contains("duplicate key") ||
                                       message.contains("唯一约束") ||
                                       message.contains("unique constraint");
                yield !isDuplicateKey;  // 非重复键错误才可重试
            }
            case VALIDATION -> false;       // 验证错误（包括数据冲突）不可重试
            case FILE_IO -> false;          // 文件问题通常无法自动恢复
            case NETWORK -> true;           // 网络错误可重试
            case AI_SERVICE -> true;        // AI 调用失败可重试
            case SYSTEM -> {
                // 内存不足不可重试，其他系统错误可重试
                String message = exception.getMessage();
                yield message == null ||
                      !(message.contains("OutOfMemory") ||
                        message.contains("内存不足"));
            }
            case UNKNOWN -> false;          // 未知错误默认不可重试
        };
    }

    // ==================== toString (调试用) ====================

    @Override
    public String toString() {
        return String.format(
            "DiagnosticFailure[file=%s, table=%s, phase=%s, code=%s, retryable=%s, retry=%d]",
            fileName, tableName, importPhase, structuredError.errorCode(), retryable, retryCount
        );
    }
}
