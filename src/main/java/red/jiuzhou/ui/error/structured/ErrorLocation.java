package red.jiuzhou.ui.error.structured;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 错误位置信息 - 支持精确定位到文件的具体位置
 *
 * @author Claude
 * @version 1.0
 */
public record ErrorLocation(
    String filePath,     // 文件绝对路径
    String fileName,     // 文件名
    int line,            // 行号 (1-based)
    int column,          // 列号 (1-based)
    int endLine,         // 结束行号
    int endColumn,       // 结束列号
    String lineContent,  // 该行内容
    String highlight     // 高亮标记
) {

    /**
     * 创建行级位置
     */
    public static ErrorLocation line(String filePath, int lineNum) {
        String fileName = extractFileName(filePath);
        String content = readLineContent(filePath, lineNum);
        return new ErrorLocation(filePath, fileName, lineNum, 1, lineNum, -1, content, null);
    }

    /**
     * 创建精确位置 (带列号)
     */
    public static ErrorLocation precise(String filePath, int line, int col, int endCol) {
        String fileName = extractFileName(filePath);
        String content = readLineContent(filePath, line);
        return new ErrorLocation(filePath, fileName, line, col, line, endCol, content, null);
    }

    /**
     * 创建范围位置
     */
    public static ErrorLocation range(String filePath, int startLine, int startCol,
                                       int endLine, int endCol) {
        String fileName = extractFileName(filePath);
        String content = readLineContent(filePath, startLine);
        return new ErrorLocation(filePath, fileName, startLine, startCol, endLine, endCol, content, null);
    }

    /**
     * 从配置键创建位置 (用于 YAML 配置)
     */
    public static ErrorLocation fromConfigKey(String filePath, String configKey) {
        int lineNum = findConfigKeyLine(filePath, configKey);
        if (lineNum > 0) {
            return line(filePath, lineNum);
        }
        return new ErrorLocation(filePath, extractFileName(filePath), 1, 1, 1, -1, null, null);
    }

    /**
     * 格式化为可点击的位置字符串 (IDE风格)
     * 例如: "application.yml:5:12"
     */
    public String toClickableString() {
        if (column > 0) {
            return "%s:%d:%d".formatted(fileName, line, column);
        }
        return "%s:%d".formatted(fileName, line);
    }

    /**
     * 格式化为完整路径的位置字符串
     */
    public String toFullPathString() {
        if (column > 0) {
            return "%s:%d:%d".formatted(filePath, line, column);
        }
        return "%s:%d".formatted(filePath, line);
    }

    /**
     * 判断是否可导航
     */
    public boolean isNavigable() {
        return filePath != null && !filePath.isEmpty() && line > 0;
    }

    /**
     * 生成下划线标记 (Rust风格)
     */
    public String generateUnderline(String message) {
        if (lineContent == null || column <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(Math.max(0, column - 1)));

        int highlightLen = endColumn > column ? endColumn - column :
                           Math.min(10, lineContent.length() - column + 1);
        highlightLen = Math.max(1, highlightLen);

        sb.append("^".repeat(highlightLen));
        if (message != null && !message.isEmpty()) {
            sb.append(" ").append(message);
        }

        return sb.toString();
    }

    // ==================== 私有辅助方法 ====================

    private static String extractFileName(String filePath) {
        if (filePath == null) return "";
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private static String readLineContent(String filePath, int lineNum) {
        if (filePath == null || lineNum <= 0) return null;

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return null;

            List<String> lines = Files.readAllLines(path);
            if (lineNum <= lines.size()) {
                return lines.get(lineNum - 1);
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
        return null;
    }

    private static int findConfigKeyLine(String filePath, String configKey) {
        if (filePath == null || configKey == null) return -1;

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) return -1;

            List<String> lines = Files.readAllLines(path);
            String[] keyParts = configKey.split("\\.");
            String lastPart = keyParts[keyParts.length - 1];

            // 简单匹配：查找包含最后一个键部分的行
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith(lastPart + ":") || trimmed.startsWith(lastPart + " :")) {
                    return i + 1;
                }
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
        return -1;
    }
}
