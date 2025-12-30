package red.jiuzhou.server;

import org.junit.jupiter.api.Test;
import red.jiuzhou.server.dao.ServerConfigFileDao;
import red.jiuzhou.server.model.ServerConfigFile;
import red.jiuzhou.server.service.ServerLogAnalyzer;

import java.util.List;

/**
 * 服务器日志分析器测试
 */
public class ServerLogAnalyzerTest {

    @Test
    public void testAnalyzeMainServerLogs() {
        // 分析 MainServer 日志
        ServerLogAnalyzer analyzer = new ServerLogAnalyzer();

        String logDir = "d:/AionReal58/AionServer/MainServer/log";

        System.out.println("========================================");
        System.out.println("开始分析服务器日志: " + logDir);
        System.out.println("========================================");

        ServerLogAnalyzer.AnalysisResult result = analyzer.analyzeLogDirectory(logDir);

        if (result.getErrorMessage() != null) {
            System.err.println("❌ 分析失败: " + result.getErrorMessage());
            return;
        }

        System.out.println("\n📊 分析结果:");
        System.out.println("发现 " + result.getXmlFiles().size() + " 个 XML 文件\n");

        // 显示前 20 个文件
        int count = 0;
        for (String fileName : result.getXmlFiles().keySet()) {
            ServerLogAnalyzer.FileLoadInfo info = result.getXmlFiles().get(fileName);
            String status = info.isSuccessfullyLoaded() ? "✅" : "❌";
            System.out.printf("%s %s", status, fileName);
            if (info.getErrorCount() > 0) {
                System.out.printf(" (%d 个错误)", info.getErrorCount());
            }
            System.out.println();

            count++;
            if (count >= 20) {
                System.out.println("... (还有 " + (result.getXmlFiles().size() - 20) + " 个文件)");
                break;
            }
        }

        // 保存到数据库
        System.out.println("\n💾 保存分析结果到数据库...");
        int savedCount = analyzer.saveAnalysisResult(result, "MainServer");
        System.out.println("✅ 成功保存 " + savedCount + " 条记录");

        // 查询数据库验证
        System.out.println("\n📋 数据库验证:");
        ServerConfigFileDao dao = new ServerConfigFileDao();

        List<ServerConfigFile> serverLoaded = dao.findServerLoaded();
        System.out.println("服务器已加载文件: " + serverLoaded.size());

        List<ServerConfigFile> critical = dao.findCriticalFiles();
        System.out.println("核心配置文件: " + critical.size());

        System.out.println("\n🔥 核心配置文件列表:");
        for (ServerConfigFile file : critical) {
            System.out.printf("  - %s (表名: %s, 分类: %s)\n",
                file.getFileName(), file.getTableName(), file.getFileCategory());
        }
    }

    @Test
    public void testQueryServerConfigFiles() {
        ServerConfigFileDao dao = new ServerConfigFileDao();

        System.out.println("========================================");
        System.out.println("查询服务器配置文件清单");
        System.out.println("========================================\n");

        // 查询所有文件
        List<ServerConfigFile> all = dao.findAll();
        System.out.println("📊 总文件数: " + all.size());

        // 按加载状态统计
        List<ServerConfigFile> loaded = dao.findServerLoaded();
        System.out.println("✅ 服务器已加载: " + loaded.size());

        // 按优先级统计
        List<ServerConfigFile> critical = dao.findByPriority(1);
        List<ServerConfigFile> important = dao.findByPriority(2);
        List<ServerConfigFile> normal = dao.findByPriority(3);

        System.out.println("\n优先级分布:");
        System.out.println("  🔥 核心配置 (优先级1): " + critical.size());
        System.out.println("  ⚠️ 重要配置 (优先级2): " + important.size());
        System.out.println("  📄 一般配置 (优先级3): " + normal.size());

        // 按分类统计
        System.out.println("\n分类统计:");
        String[] categories = {"items", "skills", "quests", "npcs", "worlds", "config", "other"};
        for (String category : categories) {
            List<ServerConfigFile> files = dao.findByCategory(category);
            if (!files.isEmpty()) {
                System.out.printf("  %s: %d 个文件\n", category, files.size());
            }
        }
    }
}
