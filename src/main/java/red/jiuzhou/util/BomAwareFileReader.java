package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * BOM-aware 文件读取器
 *
 * 功能：
 * - 自动检测并跳过 BOM 字节
 * - 支持 UTF-8、UTF-16BE、UTF-16LE、UTF-16 等编码
 * - 避免 XML 解析时的 "前言中不允许有内容" 错误
 *
 * @author Claude
 * @date 2025-12-29
 */
public class BomAwareFileReader {

    private static final Logger log = LoggerFactory.getLogger(BomAwareFileReader.class);

    /**
     * 读取文件内容（自动跳过 BOM）
     *
     * @param file     文件对象
     * @param encoding 编码信息
     * @return 文件内容（已去除 BOM）
     * @throws IOException IO异常
     */
    public static String readString(File file, FileEncodingDetector.EncodingInfo encoding) throws IOException {
        Charset charset = encoding.toCharset();

        // 使用 InputStreamReader 读取，它会自动处理 BOM
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, charset)) {

            StringBuilder content = new StringBuilder((int) file.length());
            char[] buffer = new char[8192];
            int read;

            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
            }

            String result = content.toString();

            // ========== 额外保险：手动移除可能残留的 BOM 字符 ==========
            // BOM 字符在转换后可能变成 U+FEFF（零宽非断空格）
            if (result.length() > 0 && result.charAt(0) == '\uFEFF') {
                log.debug("检测到并移除 BOM 字符: U+FEFF");
                result = result.substring(1);
            }

            // 移除所有 BOM 字符（某些文件可能在中间也有 BOM）
            result = result.replace("\uFEFF", "");

            log.debug("文件读取完成: {}, 长度: {} 字符", file.getName(), result.length());
            return result;
        }
    }

    /**
     * 读取文件内容（使用路径）
     *
     * @param filePath 文件路径
     * @param encoding 编码信息
     * @return 文件内容（已去除 BOM）
     * @throws IOException IO异常
     */
    public static String readString(String filePath, FileEncodingDetector.EncodingInfo encoding) throws IOException {
        return readString(new File(filePath), encoding);
    }

    /**
     * 检测并移除字符串开头的 BOM 字符
     *
     * @param content 原始内容
     * @return 去除 BOM 后的内容
     */
    public static String removeBOM(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // 移除开头的 BOM 字符
        if (content.charAt(0) == '\uFEFF') {
            log.debug("移除 BOM 字符: U+FEFF");
            content = content.substring(1);
        }

        // 移除所有 BOM 字符（包括中间的）
        content = content.replace("\uFEFF", "");

        return content;
    }

    /**
     * 检查字符串是否以 BOM 开头
     *
     * @param content 内容
     * @return true 表示以 BOM 开头
     */
    public static boolean startsWithBOM(String content) {
        return content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF';
    }

    /**
     * 读取文件的前 N 个字节（用于调试）
     *
     * @param file 文件
     * @param n    字节数
     * @return 十六进制表示的字节
     */
    public static String readFirstBytesAsHex(File file, int n) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[Math.min(n, (int) file.length())];
            int read = fis.read(bytes);

            if (read <= 0) {
                return "空文件";
            }

            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < read; i++) {
                hex.append(String.format("%02X ", bytes[i] & 0xFF));
            }
            return hex.toString().trim();

        } catch (IOException e) {
            return "读取失败: " + e.getMessage();
        }
    }

    /**
     * 诊断文件编码和 BOM 问题
     *
     * @param file 文件
     * @return 诊断报告
     */
    public static String diagnoseFile(File file) {
        StringBuilder report = new StringBuilder();
        report.append("=== 文件编码诊断 ===\n");
        report.append("文件: ").append(file.getName()).append("\n");
        report.append("大小: ").append(file.length()).append(" 字节\n");

        // 读取前 16 个字节
        String firstBytes = readFirstBytesAsHex(file, 16);
        report.append("前16字节: ").append(firstBytes).append("\n");

        // 检测编码
        FileEncodingDetector.EncodingInfo encoding = FileEncodingDetector.detect(file);
        report.append("检测编码: ").append(encoding).append("\n");
        report.append("是否有BOM: ").append(encoding.hasBOM()).append("\n");

        // 尝试读取第一行
        try {
            String content = readString(file, encoding);
            String firstLine = content.split("\n")[0];
            if (firstLine.length() > 100) {
                firstLine = firstLine.substring(0, 100) + "...";
            }
            report.append("第一行: ").append(firstLine).append("\n");
            report.append("是否以BOM字符开头: ").append(startsWithBOM(content)).append("\n");

        } catch (Exception e) {
            report.append("读取失败: ").append(e.getMessage()).append("\n");
        }

        return report.toString();
    }
}
