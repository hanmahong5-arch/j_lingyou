package red.jiuzhou.ui.error.navigation;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import red.jiuzhou.ui.ConfigEditorStage;
import red.jiuzhou.ui.error.structured.ErrorLocation;
import red.jiuzhou.ui.error.structured.StructuredError;

import java.io.File;
import java.io.IOException;

/**
 * 错误导航服务 - 支持从错误信息跳转到源位置
 *
 * @author Claude
 * @version 1.0
 */
@Service
public class ErrorNavigationService {

    private static final Logger log = LoggerFactory.getLogger(ErrorNavigationService.class);

    private ConfigEditorStage cachedConfigEditor;

    /**
     * 导航到错误位置
     */
    public void navigateToError(StructuredError error) {
        if (error == null) return;

        ErrorLocation location = error.location();
        if (location == null || !location.isNavigable()) {
            log.debug("错误位置不可导航: {}", error.errorCode());
            return;
        }

        Platform.runLater(() -> {
            String filePath = location.filePath();

            if (isConfigFile(filePath)) {
                navigateToConfigFile(location);
            } else if (isSourceFile(filePath)) {
                navigateToSourceFile(location);
            } else {
                openInSystemEditor(filePath, location.line());
            }
        });
    }

    /**
     * 导航到配置文件
     */
    private void navigateToConfigFile(ErrorLocation location) {
        try {
            ConfigEditorStage editor = getOrCreateConfigEditor();
            editor.show();
            editor.toFront();

            // 跳转到指定行
            editor.gotoLine(location.line());

            // 高亮错误位置
            if (location.column() > 0) {
                editor.highlightRange(
                    location.line(), location.column(),
                    location.endLine() > 0 ? location.endLine() : location.line(),
                    location.endColumn() > 0 ? location.endColumn() : location.column() + 10
                );
            } else {
                editor.highlightLine(location.line());
            }

            log.info("导航到配置文件: {} 第{}行", location.fileName(), location.line());
        } catch (Exception e) {
            log.error("导航到配置文件失败", e);
        }
    }

    /**
     * 导航到源代码文件 (调用外部IDE)
     */
    private void navigateToSourceFile(ErrorLocation location) {
        String ideCommand = detectIde();

        try {
            String command = switch (ideCommand) {
                case "idea" -> String.format(
                    "cmd /c idea64.exe --line %d \"%s\"",
                    location.line(), location.filePath()
                );
                case "vscode" -> String.format(
                    "cmd /c code --goto \"%s:%d:%d\"",
                    location.filePath(), location.line(),
                    location.column() > 0 ? location.column() : 1
                );
                default -> null;
            };

            if (command != null) {
                Runtime.getRuntime().exec(command);
                log.info("调用IDE导航: {}", command);
            } else {
                // 回退：使用系统默认编辑器
                openInSystemEditor(location.filePath(), location.line());
            }
        } catch (IOException e) {
            log.error("调用IDE失败", e);
            openInSystemEditor(location.filePath(), location.line());
        }
    }

    /**
     * 使用系统默认应用打开文件
     */
    private void openInSystemEditor(String filePath, int line) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                java.awt.Desktop.getDesktop().open(file);
                log.info("使用系统默认应用打开: {}", filePath);
            } else {
                log.warn("文件不存在: {}", filePath);
            }
        } catch (IOException e) {
            log.error("打开文件失败: {}", filePath, e);
        }
    }

    /**
     * 检测可用的IDE
     */
    private String detectIde() {
        // 检查 IDEA
        String ideaPath = System.getenv("IDEA_HOME");
        if (ideaPath != null && new File(ideaPath).exists()) {
            return "idea";
        }

        // 检查常见 IDEA 安装路径
        String[] ideaPaths = {
            "C:\\Program Files\\JetBrains\\IntelliJ IDEA",
            System.getProperty("user.home") + "\\AppData\\Local\\JetBrains\\Toolbox\\apps\\IDEA-U",
            System.getProperty("user.home") + "\\AppData\\Local\\JetBrains\\Toolbox\\apps\\IDEA-C"
        };
        for (String path : ideaPaths) {
            if (new File(path).exists()) {
                return "idea";
            }
        }

        // 检查 VS Code
        String vscodePath = System.getenv("VSCODE_PATH");
        if (vscodePath != null && new File(vscodePath).exists()) {
            return "vscode";
        }

        // 检查常见 VS Code 安装路径
        String[] vscodePaths = {
            "C:\\Program Files\\Microsoft VS Code\\Code.exe",
            System.getProperty("user.home") + "\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe"
        };
        for (String path : vscodePaths) {
            if (new File(path).exists()) {
                return "vscode";
            }
        }

        return "unknown";
    }

    /**
     * 判断是否为配置文件
     */
    private boolean isConfigFile(String filePath) {
        if (filePath == null) return false;
        String lower = filePath.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml") ||
               lower.endsWith(".json") || lower.endsWith(".properties") ||
               lower.endsWith(".env");
    }

    /**
     * 判断是否为Java源代码文件
     */
    private boolean isSourceFile(String filePath) {
        if (filePath == null) return false;
        return filePath.toLowerCase().endsWith(".java");
    }

    /**
     * 获取或创建配置编辑器实例
     */
    private ConfigEditorStage getOrCreateConfigEditor() {
        if (cachedConfigEditor == null || !cachedConfigEditor.isShowing()) {
            cachedConfigEditor = new ConfigEditorStage();
        }
        return cachedConfigEditor;
    }

    /**
     * 导航到配置键
     */
    public void navigateToConfigKey(String configKey) {
        Platform.runLater(() -> {
            try {
                ConfigEditorStage editor = getOrCreateConfigEditor();
                editor.show();
                editor.toFront();
                editor.navigateToKey(configKey);
                log.info("导航到配置键: {}", configKey);
            } catch (Exception e) {
                log.error("导航到配置键失败: {}", configKey, e);
            }
        });
    }
}
