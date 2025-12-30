package red.jiuzhou.validation.server;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

/**
 * XML合规性检查命令行工具
 *
 * <p>使用方法：
 * <pre>
 * # 检查单个文件
 * java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI /path/to/file.xml
 *
 * # 检查整个目录
 * java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI /path/to/directory
 *
 * # 生成报告到文件
 * java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI /path/to/directory --report report.txt
 * </pre>
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class XmlComplianceCheckCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String path = args[0];
        String reportPath = null;

        // 解析参数
        for (int i = 1; i < args.length; i++) {
            if ("--report".equals(args[i]) && i + 1 < args.length) {
                reportPath = args[i + 1];
                i++;
            }
        }

        File target = new File(path);
        if (!target.exists()) {
            System.err.println("错误: 文件或目录不存在: " + path);
            System.exit(1);
        }

        XmlServerComplianceChecker checker = new XmlServerComplianceChecker();
        List<XmlServerComplianceChecker.CheckResult> results;

        System.out.println("=" .repeat(80));
        System.out.println("XML服务器合规性检查工具");
        System.out.println("=" .repeat(80));
        System.out.println();

        if (target.isFile()) {
            System.out.println("检查文件: " + target.getAbsolutePath());
            System.out.println();
            XmlServerComplianceChecker.CheckResult result = checker.check(target);
            System.out.println(result.getSummary());

            if (!result.isCompliant()) {
                System.err.println("\n⚠️  警告: 该文件不符合服务器加载要求，部署后可能导致服务器错误！");
                System.exit(1);
            } else {
                System.out.println("\n✅ 该文件符合服务器加载要求，可以安全部署。");
            }
        } else if (target.isDirectory()) {
            System.out.println("检查目录: " + target.getAbsolutePath());
            System.out.println();
            results = checker.checkDirectory(target);

            if (results.isEmpty()) {
                System.out.println("目录中没有XML文件。");
                System.exit(0);
            }

            String report = checker.generateBatchReport(results);
            System.out.println(report);

            // 输出到文件
            if (reportPath != null) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(reportPath))) {
                    writer.println(report);
                    System.out.println("\n报告已保存到: " + reportPath);
                } catch (Exception e) {
                    System.err.println("保存报告失败: " + e.getMessage());
                }
            }

            long nonCompliantCount = results.stream()
                .filter(r -> !r.isCompliant())
                .count();

            if (nonCompliantCount > 0) {
                System.err.println(String.format("\n⚠️  警告: %d 个文件不符合服务器要求！", nonCompliantCount));
                System.exit(1);
            } else {
                System.out.println("\n✅ 所有文件都符合服务器加载要求。");
            }
        } else {
            System.err.println("错误: 既不是文件也不是目录: " + path);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("XML服务器合规性检查工具");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI <文件或目录> [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --report <文件>    将检查报告保存到指定文件");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 检查单个文件");
        System.out.println("  java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI items.xml");
        System.out.println();
        System.out.println("  # 检查整个目录");
        System.out.println("  java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI /path/to/xml/directory");
        System.out.println();
        System.out.println("  # 生成报告文件");
        System.out.println("  java -cp dbxmltool.jar red.jiuzhou.validation.server.XmlComplianceCheckCLI /path/to/xml/directory --report report.txt");
    }
}
