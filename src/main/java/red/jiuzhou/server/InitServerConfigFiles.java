package red.jiuzhou.server;

import red.jiuzhou.server.dao.ServerConfigFileDao;
import red.jiuzhou.server.service.ServerLogAnalyzer;

/**
 * 初始化服务器配置文件清单
 * 独立运行的初始化程序
 */
public class InitServerConfigFiles {

    public static void main(String[] args) {
        String logDir = args.length > 0 ? args[0] : "d:/AionReal58/AionServer/MainServer/log";

        System.out.println("========================================");
        System.out.println("服务器配置文件清单初始化");
        System.out.println("========================================");
        System.out.println("日志目录: " + logDir);
        System.out.println();

        try {
            ServerLogAnalyzer analyzer = new ServerLogAnalyzer();

            // 分析日志
            System.out.println("📊 正在分析日志...");
            ServerLogAnalyzer.AnalysisResult result = analyzer.analyzeLogDirectory(logDir);

            if (result.getErrorMessage() != null) {
                System.err.println("❌ 分析失败: " + result.getErrorMessage());
                System.exit(1);
            }

            System.out.println("✅ 发现 " + result.getXmlFiles().size() + " 个 XML 文件");

            // 保存到数据库
            System.out.println("\n💾 保存到数据库...");
            int savedCount = analyzer.saveAnalysisResult(result, "MainServer");

            System.out.println("✅ 成功保存 " + savedCount + " 条记录");

            // 统计信息
            System.out.println("\n📋 统计信息:");
            ServerConfigFileDao dao = new ServerConfigFileDao();

            System.out.println("  ✅ 服务器已加载: " + dao.findServerLoaded().size());
            System.out.println("  🔥 核心配置文件: " + dao.findCriticalFiles().size());
            System.out.println("  ⚠️ 重要配置文件: " + dao.findByPriority(2).size());
            System.out.println("  📄 一般配置文件: " + dao.findByPriority(3).size());

            System.out.println("\n========================================");
            System.out.println("✅ 初始化完成！");
            System.out.println("========================================");
            System.out.println("\n💡 提示:");
            System.out.println("  1. 启动应用");
            System.out.println("  2. 点击工具栏「📋 配置清单」按钮");
            System.out.println("  3. 查看服务器配置文件清单");

        } catch (Exception e) {
            System.err.println("❌ 初始化失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
