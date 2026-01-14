package red.jiuzhou.util;

/**
 * Simple tool to fix file_encoding_metadata table structure
 * No external dependencies except DatabaseUtil
 *
 * @date 2026-01-14
 */
public class FixEncodingMetadataTableSimple {

    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("开始修复 file_encoding_metadata 表结构...");
            System.out.println("========================================");
            System.out.println();

            String scriptPath = "D:\\workspace\\dbxmlTool\\scripts\\fix_encoding_metadata_table.sql";
            System.out.println("执行 SQL 脚本: " + scriptPath);

            DatabaseUtil.executeSqlScript(scriptPath);

            System.out.println();
            System.out.println("========================================");
            System.out.println("✅ file_encoding_metadata 表修复完成！");
            System.out.println("========================================");
            System.out.println();
            System.out.println("表结构已更新为完整版本，包含所有必需字段：");
            System.out.println("  - original_encoding, has_bom");
            System.out.println("  - file_size_bytes");
            System.out.println("  - last_import_time, last_export_time");
            System.out.println("  - import_count, export_count");
            System.out.println("  - 等等...");
            System.out.println();
            System.out.println("现在可以重新运行应用程序测试了！");

        } catch (Exception e) {
            System.err.println();
            System.err.println("========================================");
            System.err.println("❌ 修复失败！");
            System.err.println("========================================");
            System.err.println();
            System.err.println("错误信息: " + e.getMessage());
            System.err.println();
            System.err.println("可能的原因：");
            System.err.println("1. PostgreSQL 服务未启动");
            System.err.println("2. 数据库连接配置错误（application.yml）");
            System.err.println("3. 权限不足");
            System.err.println();
            e.printStackTrace();
            System.exit(1);
        }
    }
}
