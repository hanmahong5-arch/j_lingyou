package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件编码自动检测器
 *
 * 支持多种检测策略：
 * 1. BOM标记检测（最可靠）
 * 2. XML声明解析
 * 3. 系统file命令检测
 *
 * @author Claude
 * @date 2025-12-28
 */
public class FileEncodingDetector {

    private static final Logger log = LoggerFactory.getLogger(FileEncodingDetector.class);

    /**
     * 检测文件编码
     *
     * @param file XML文件
     * @return 编码信息
     */
    public static EncodingInfo detect(File file) {
        try {
            // 1. 检测BOM标记（最可靠）
            EncodingInfo bomDetected = detectByBOM(file);
            if (bomDetected != null) {
                log.debug("通过BOM检测到编码: {}", bomDetected);
                return bomDetected;
            }

            // 2. 读取XML声明
            EncodingInfo xmlDeclared = detectByXmlDeclaration(file);
            if (xmlDeclared != null) {
                log.debug("通过XML声明检测到编码: {}", xmlDeclared);
                return xmlDeclared;
            }

            // 3. 使用系统file命令（仅在Git Bash环境可用）
            EncodingInfo sysDetected = detectBySystemCommand(file);
            if (sysDetected != null) {
                log.debug("通过file命令检测到编码: {}", sysDetected);
                return sysDetected;
            }

            // 4. 默认返回UTF-16（保持向后兼容）
            log.warn("无法检测文件编码，使用默认UTF-16: {}", file.getName());
            return new EncodingInfo("UTF-16", false);

        } catch (IOException e) {
            log.error("检测文件编码时出错: {}", file.getName(), e);
            return new EncodingInfo("UTF-16", false);
        }
    }

    /**
     * 通过BOM检测编码
     */
    private static EncodingInfo detectByBOM(File file) throws IOException {
        byte[] bom = new byte[4];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(bom);
            if (read < 2) return null;
        }

        // UTF-16BE BOM: FE FF
        if (bom[0] == (byte)0xFE && bom[1] == (byte)0xFF) {
            return new EncodingInfo("UTF-16BE", true);
        }

        // UTF-16LE BOM: FF FE
        if (bom[0] == (byte)0xFF && bom[1] == (byte)0xFE) {
            return new EncodingInfo("UTF-16LE", true);
        }

        // UTF-8 BOM: EF BB BF
        if (bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
            return new EncodingInfo("UTF-8", true);
        }

        return null;
    }

    /**
     * 通过XML声明检测编码
     */
    private static EncodingInfo detectByXmlDeclaration(File file) {
        // 尝试不同编码读取第一行
        String firstLine = null;

        // 尝试UTF-16BE
        firstLine = readFirstLine(file, StandardCharsets.UTF_16BE);
        if (firstLine != null && firstLine.startsWith("<?xml")) {
            return extractEncodingFromXmlDecl(firstLine, "UTF-16BE");
        }

        // 尝试UTF-16LE
        firstLine = readFirstLine(file, StandardCharsets.UTF_16LE);
        if (firstLine != null && firstLine.startsWith("<?xml")) {
            return extractEncodingFromXmlDecl(firstLine, "UTF-16LE");
        }

        // 尝试UTF-16（自动检测大小端）
        firstLine = readFirstLine(file, StandardCharsets.UTF_16);
        if (firstLine != null && firstLine.startsWith("<?xml")) {
            return extractEncodingFromXmlDecl(firstLine, "UTF-16");
        }

        // 尝试UTF-8
        firstLine = readFirstLine(file, StandardCharsets.UTF_8);
        if (firstLine != null && firstLine.startsWith("<?xml")) {
            return extractEncodingFromXmlDecl(firstLine, "UTF-8");
        }

        return null;
    }

    /**
     * 从XML声明中提取编码信息
     */
    private static EncodingInfo extractEncodingFromXmlDecl(String xmlDecl, String defaultEncoding) {
        if (xmlDecl.contains("encoding")) {
            Pattern pattern = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(xmlDecl);
            if (matcher.find()) {
                String encoding = normalizeEncoding(matcher.group(1));
                return new EncodingInfo(encoding, false);
            }
        }
        // XML声明中未指定编码，使用读取成功的编码
        return new EncodingInfo(defaultEncoding, false);
    }

    /**
     * 使用系统file命令检测（Git Bash环境）
     */
    private static EncodingInfo detectBySystemCommand(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder("file", "-b", "--mime-encoding", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();

            if (output != null && !output.trim().isEmpty()) {
                String encoding = normalizeEncoding(output.trim());
                return new EncodingInfo(encoding, false);
            }
        } catch (Exception e) {
            // file命令不可用，静默失败
            log.trace("file命令不可用: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 读取文件第一行
     */
    private static String readFirstLine(File file, Charset charset) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 规范化编码名称
     */
    private static String normalizeEncoding(String encoding) {
        if (encoding == null) {
            return "UTF-16";
        }

        encoding = encoding.toUpperCase().trim();

        // 规范化常见变体
        if (encoding.equals("UTF16BE") || encoding.equals("UTF-16-BE")) {
            return "UTF-16BE";
        }
        if (encoding.equals("UTF16LE") || encoding.equals("UTF-16-LE")) {
            return "UTF-16LE";
        }
        if (encoding.equals("UTF8") || encoding.equals("UTF-8")) {
            return "UTF-8";
        }
        if (encoding.equals("UTF16") || encoding.startsWith("UTF-16")) {
            return "UTF-16";
        }

        return encoding;
    }

    /**
     * 编码信息类
     */
    public static class EncodingInfo {
        private final String encoding;
        private final boolean hasBOM;

        public EncodingInfo(String encoding, boolean hasBOM) {
            this.encoding = encoding;
            this.hasBOM = hasBOM;
        }

        public String getEncoding() {
            return encoding;
        }

        public boolean hasBOM() {
            return hasBOM;
        }

        public boolean isUTF16() {
            return encoding != null && encoding.startsWith("UTF-16");
        }

        public Charset toCharset() {
            if ("UTF-16BE".equals(encoding)) {
                return StandardCharsets.UTF_16BE;
            }
            if ("UTF-16LE".equals(encoding)) {
                return StandardCharsets.UTF_16LE;
            }
            if ("UTF-16".equals(encoding)) {
                return StandardCharsets.UTF_16;
            }
            if ("UTF-8".equals(encoding)) {
                return StandardCharsets.UTF_8;
            }
            // 其他编码尝试解析
            try {
                return Charset.forName(encoding);
            } catch (Exception e) {
                log.warn("不支持的编码 {}, 使用UTF-16", encoding);
                return StandardCharsets.UTF_16;
            }
        }

        @Override
        public String toString() {
            return encoding + (hasBOM ? " (with BOM)" : "");
        }
    }
}
