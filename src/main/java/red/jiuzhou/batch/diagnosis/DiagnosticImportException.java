package red.jiuzhou.batch.diagnosis;

/**
 * Diagnostic Import Exception
 *
 * 包装 DiagnosticFailure 的自定义异常，用于批量导入过程中抛出
 * 携带完整的诊断信息，便于上层捕获和处理
 *
 * 使用场景：
 * 1. BatchXmlImporter.importSingleXml() 捕获异常后包装为此异常
 * 2. BatchXmlImporter.importBatchXml() 捕获此异常并提取 DiagnosticFailure
 * 3. 确保失败信息不丢失，完整传递到诊断系统
 *
 * @author Claude AI
 * @date 2026-01-15
 */
public class DiagnosticImportException extends RuntimeException {

    private final DiagnosticFailure diagnosticFailure;

    /**
     * 构造函数（推荐）
     *
     * @param failure 诊断失败记录
     * @param cause 原始异常（用于保留堆栈跟踪）
     */
    public DiagnosticImportException(DiagnosticFailure failure, Throwable cause) {
        super(buildMessage(failure), cause);
        this.diagnosticFailure = failure;
    }

    /**
     * 简化构造函数（无原始异常）
     *
     * @param failure 诊断失败记录
     */
    public DiagnosticImportException(DiagnosticFailure failure) {
        super(buildMessage(failure));
        this.diagnosticFailure = failure;
    }

    /**
     * 获取诊断失败记录
     */
    public DiagnosticFailure getDiagnosticFailure() {
        return diagnosticFailure;
    }

    /**
     * 构建用户友好的异常消息
     */
    private static String buildMessage(DiagnosticFailure failure) {
        if (failure == null) {
            return "导入失败（无详细信息）";
        }

        return String.format(
            "导入失败: %s [%s] - %s",
            failure.fileName(),
            failure.getErrorCode(),
            failure.structuredError().title()
        );
    }

    /**
     * 检查是否为可重试的错误
     */
    public boolean isRetryable() {
        return diagnosticFailure != null && diagnosticFailure.retryable();
    }

    /**
     * 获取错误摘要（用于日志）
     */
    public String getErrorSummary() {
        return diagnosticFailure != null
            ? diagnosticFailure.getErrorSummary()
            : getMessage();
    }

    /**
     * 获取人类可读的错误详情
     */
    public String getErrorDetail() {
        return diagnosticFailure != null
            ? diagnosticFailure.getErrorDetail()
            : getMessage();
    }

    @Override
    public String toString() {
        return String.format(
            "DiagnosticImportException[file=%s, code=%s, retryable=%s]",
            diagnosticFailure != null ? diagnosticFailure.fileName() : "unknown",
            diagnosticFailure != null ? diagnosticFailure.getErrorCode() : "unknown",
            isRetryable()
        );
    }
}
