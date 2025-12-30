#!/bin/bash
# 服务器日志分析脚本
# 分析 MainServer/NPCServer 日志，提取服务器实际加载的 XML 文件列表

LOG_DIR="${1:-d:/AionReal58/AionServer/MainServer/log}"

echo "========================================="
echo "服务器日志分析工具"
echo "========================================="
echo "日志目录: $LOG_DIR"
echo ""

if [ ! -d "$LOG_DIR" ]; then
    echo "❌ 错误: 日志目录不存在"
    echo "用法: $0 [日志目录路径]"
    exit 1
fi

echo "正在提取 XML 文件加载记录..."
echo ""

# 提取所有 XML 文件名并统计
grep -roh "[a-zA-Z0-9_-]*\.xml" "$LOG_DIR" 2>/dev/null | \
    sort -u | \
    awk '{
        count++;
        print count". "$0
    }'

TOTAL=$(grep -roh "[a-zA-Z0-9_-]*\.xml" "$LOG_DIR" 2>/dev/null | sort -u | wc -l)

echo ""
echo "========================================="
echo "✅ 分析完成！共发现 $TOTAL 个 XML 文件"
echo "========================================="
echo ""
echo "💡 提示:"
echo "  1. 在应用中打开「📋 配置清单」窗口"
echo "  2. 点击「分析服务器日志」按钮"
echo "  3. 选择日志目录: $LOG_DIR"
echo "  4. 系统会自动保存到数据库"
echo ""
