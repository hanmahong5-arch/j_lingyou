package red.jiuzhou.ui.error.structured;

/**
 * 全局错误码定义 - 参考 Rust 编译器错误码
 *
 * <p>编码规范:
 * <ul>
 *   <li>CFG-XXXX: 配置相关错误</li>
 *   <li>DB-XXXX:  数据库相关错误</li>
 *   <li>AI-XXXX:  AI服务相关错误</li>
 *   <li>IO-XXXX:  文件IO相关错误</li>
 *   <li>SYS-XXXX: 系统级错误</li>
 *   <li>VAL-XXXX: 验证错误</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public enum ErrorCodes {

    // ==================== 配置错误 (CFG-0001 ~ CFG-0999) ====================

    /** 缺少必填配置项 */
    CFG_MISSING_REQUIRED("CFG-0001", "缺少必填配置项", ErrorLevel.ERROR,
        ErrorCategory.CONFIGURATION,
        "配置文件中缺少必要的配置项，应用无法正常启动"),

    /** 配置格式错误 */
    CFG_INVALID_FORMAT("CFG-0002", "配置格式错误", ErrorLevel.ERROR,
        ErrorCategory.CONFIGURATION,
        "配置文件格式不正确，无法解析"),

    /** AI服务未配置 */
    CFG_AI_NOT_CONFIGURED("CFG-0003", "AI服务未配置", ErrorLevel.WARNING,
        ErrorCategory.CONFIGURATION,
        "AI功能相关配置缺失，部分功能不可用"),

    /** 数据库连接配置错误 */
    CFG_DB_CONNECTION("CFG-0004", "数据库连接失败", ErrorLevel.ERROR,
        ErrorCategory.CONFIGURATION,
        "无法连接到配置的数据库地址"),

    /** 配置路径不存在 */
    CFG_PATH_NOT_EXIST("CFG-0005", "配置路径不存在", ErrorLevel.WARNING,
        ErrorCategory.CONFIGURATION,
        "配置的文件路径在系统中不存在"),

    /** 配置值无效 */
    CFG_INVALID_VALUE("CFG-0006", "配置值无效", ErrorLevel.ERROR,
        ErrorCategory.CONFIGURATION,
        "配置项的值不符合要求"),

    /** 配置文件不存在 */
    CFG_FILE_NOT_FOUND("CFG-0007", "配置文件不存在", ErrorLevel.ERROR,
        ErrorCategory.CONFIGURATION,
        "application.yml 配置文件未找到"),

    // ==================== 数据库错误 (DB-0001 ~ DB-0999) ====================

    /** 数据库连接超时 */
    DB_CONNECTION_TIMEOUT("DB-0001", "数据库连接超时", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "数据库连接超过最大等待时间"),

    /** 数据库查询失败 */
    DB_QUERY_FAILED("DB-0002", "数据库查询失败", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "执行SQL查询时发生错误"),

    /** 事务提交失败 */
    DB_TRANSACTION_FAILED("DB-0003", "事务提交失败", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "数据库事务无法正常提交"),

    /** 数据库连接丢失 */
    DB_CONNECTION_LOST("DB-0004", "数据库连接丢失", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "与数据库的连接已断开"),

    /** SQL语法错误 */
    DB_SQL_SYNTAX("DB-0005", "SQL语法错误", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "SQL语句存在语法错误"),

    /** 数据库访问被拒绝 */
    DB_ACCESS_DENIED("DB-0006", "数据库访问被拒绝", ErrorLevel.ERROR,
        ErrorCategory.DATABASE,
        "用户名或密码错误，或权限不足"),

    // ==================== AI服务错误 (AI-0001 ~ AI-0999) ====================

    /** API密钥无效 */
    AI_API_KEY_INVALID("AI-0001", "API密钥无效", ErrorLevel.ERROR,
        ErrorCategory.AI_SERVICE,
        "提供的AI服务API密钥无效或已过期"),

    /** AI服务不可用 */
    AI_SERVICE_UNAVAILABLE("AI-0002", "AI服务不可用", ErrorLevel.WARNING,
        ErrorCategory.AI_SERVICE,
        "AI服务暂时不可用，请稍后重试"),

    /** API配额已用尽 */
    AI_QUOTA_EXCEEDED("AI-0003", "API配额已用尽", ErrorLevel.WARNING,
        ErrorCategory.AI_SERVICE,
        "AI服务API调用配额已达上限"),

    /** AI请求超时 */
    AI_REQUEST_TIMEOUT("AI-0004", "AI请求超时", ErrorLevel.WARNING,
        ErrorCategory.AI_SERVICE,
        "AI服务响应超时"),

    /** AI响应解析失败 */
    AI_RESPONSE_PARSE_ERROR("AI-0005", "AI响应解析失败", ErrorLevel.WARNING,
        ErrorCategory.AI_SERVICE,
        "无法解析AI服务返回的响应"),

    // ==================== 文件IO错误 (IO-0001 ~ IO-0999) ====================

    /** 文件未找到 */
    IO_FILE_NOT_FOUND("IO-0001", "文件未找到", ErrorLevel.ERROR,
        ErrorCategory.FILE_IO,
        "指定的文件在系统中不存在"),

    /** 文件权限不足 */
    IO_PERMISSION_DENIED("IO-0002", "文件权限不足", ErrorLevel.ERROR,
        ErrorCategory.FILE_IO,
        "没有足够的权限访问指定文件"),

    /** 编码转换错误 */
    IO_ENCODING_ERROR("IO-0003", "编码转换错误", ErrorLevel.WARNING,
        ErrorCategory.FILE_IO,
        "文件编码转换过程中发生错误"),

    /** 文件写入失败 */
    IO_WRITE_FAILED("IO-0004", "文件写入失败", ErrorLevel.ERROR,
        ErrorCategory.FILE_IO,
        "无法写入文件"),

    /** 文件读取失败 */
    IO_READ_FAILED("IO-0005", "文件读取失败", ErrorLevel.ERROR,
        ErrorCategory.FILE_IO,
        "无法读取文件内容"),

    /** XML解析错误 */
    IO_XML_PARSE_ERROR("IO-0006", "XML解析错误", ErrorLevel.ERROR,
        ErrorCategory.FILE_IO,
        "XML文件格式不正确"),

    // ==================== 系统错误 (SYS-0001 ~ SYS-0999) ====================

    /** 内存不足 */
    SYS_OUT_OF_MEMORY("SYS-0001", "内存不足", ErrorLevel.FATAL,
        ErrorCategory.SYSTEM,
        "系统可用内存不足"),

    /** 未捕获的异常 */
    SYS_UNCAUGHT_EXCEPTION("SYS-0002", "未捕获的异常", ErrorLevel.ERROR,
        ErrorCategory.SYSTEM,
        "发生了未处理的系统异常"),

    /** 未知错误 */
    SYS_UNKNOWN("SYS-9999", "未知错误", ErrorLevel.ERROR,
        ErrorCategory.UNKNOWN,
        "发生了未知错误");

    private final String code;
    private final String title;
    private final ErrorLevel level;
    private final ErrorCategory category;
    private final String description;

    ErrorCodes(String code, String title, ErrorLevel level,
               ErrorCategory category, String description) {
        this.code = code;
        this.title = title;
        this.level = level;
        this.category = category;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public ErrorLevel getLevel() {
        return level;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 生成错误文档URL
     */
    public String getDocUrl() {
        return "https://docs.example.com/errors/" + code.toLowerCase().replace("-", "/");
    }

    /**
     * 根据错误码字符串查找枚举
     */
    public static ErrorCodes fromCode(String code) {
        for (ErrorCodes ec : values()) {
            if (ec.code.equals(code)) {
                return ec;
            }
        }
        return SYS_UNKNOWN;
    }

    /**
     * 根据异常类型推断错误码
     */
    public static ErrorCodes fromException(Throwable t) {
        if (t == null) return SYS_UNKNOWN;

        String className = t.getClass().getName().toLowerCase();
        String message = t.getMessage() != null ? t.getMessage().toLowerCase() : "";

        // SQL相关
        if (className.contains("sqlexception")) {
            if (message.contains("timeout")) return DB_CONNECTION_TIMEOUT;
            if (message.contains("access denied")) return DB_ACCESS_DENIED;
            if (message.contains("syntax")) return DB_SQL_SYNTAX;
            if (message.contains("connection")) return DB_CONNECTION_LOST;
            return DB_QUERY_FAILED;
        }

        // 配置相关
        if (message.contains("api") && message.contains("key")) {
            return AI_API_KEY_INVALID;
        }
        if (message.contains("未配置") || message.contains("not configured")) {
            return CFG_AI_NOT_CONFIGURED;
        }

        // IO相关
        if (className.contains("filenotfound")) return IO_FILE_NOT_FOUND;
        if (className.contains("accessdenied")) return IO_PERMISSION_DENIED;
        if (className.contains("xml")) return IO_XML_PARSE_ERROR;

        // 内存相关
        if (className.contains("outofmemory")) return SYS_OUT_OF_MEMORY;

        return SYS_UNKNOWN;
    }
}
