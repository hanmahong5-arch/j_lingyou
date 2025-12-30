#!/bin/bash
# XML文件编码检查和转换工具
# 用途: 批量检查XML文件编码，自动转换UTF-16为UTF-8
# 日期: 2025-12-28

echo "========================================="
echo "XML文件编码检查和转换工具"
echo "========================================="
echo ""

# 配置
XML_DIR="D:/AionReal58/AionMap/XML"
BACKUP_SUFFIX=".utf16.bak"
REPORT_FILE="encoding_report_$(date +%Y%m%d_%H%M%S).txt"

# 计数器
total_files=0
utf16_files=0
converted_files=0
failed_files=0

# 检查目录
if [ ! -d "$XML_DIR" ]; then
    echo "❌ 错误: 目录不存在: $XML_DIR"
    exit 1
fi

echo "📁 扫描目录: $XML_DIR"
echo "📊 报告文件: $REPORT_FILE"
echo ""

# 创建报告文件
{
    echo "========================================="
    echo "XML文件编码检查报告"
    echo "日期: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "========================================="
    echo ""
} > "$REPORT_FILE"

# 扫描所有XML文件（非 allNodeXml 目录）
find "$XML_DIR" -name "*.xml" -type f ! -path "*/allNodeXml/*" | while read -r xmlfile; do
    total_files=$((total_files + 1))

    # 检测文件编码
    encoding=$(file -b --mime-encoding "$xmlfile")
    file_size=$(du -h "$xmlfile" | cut -f1)

    echo "检查: $(basename "$xmlfile") [$file_size] - $encoding"

    # 记录到报告
    {
        echo "文件: $(basename "$xmlfile")"
        echo "路径: $xmlfile"
        echo "大小: $file_size"
        echo "编码: $encoding"
    } >> "$REPORT_FILE"

    # 如果是UTF-16编码，进行转换
    if [[ "$encoding" == *"utf-16"* ]] || [[ "$encoding" == *"UTF-16"* ]]; then
        utf16_files=$((utf16_files + 1))

        echo "  ⚠️  检测到UTF-16编码，准备转换..."

        # 确定具体的UTF-16变体
        if [[ "$encoding" == *"be"* ]] || [[ "$encoding" == *"BE"* ]]; then
            from_encoding="UTF-16BE"
        else
            from_encoding="UTF-16LE"
        fi

        # 生成新文件名
        base_name="${xmlfile%.xml}"
        utf8_file="${base_name}_utf8.xml"

        # 转换编码
        if iconv -f "$from_encoding" -t UTF-8 "$xmlfile" > "$utf8_file" 2>/dev/null; then
            # 修正XML声明
            sed -i '1s/UTF-16/UTF-8/I' "$utf8_file"

            # 验证转换结果
            new_encoding=$(file -b --mime-encoding "$utf8_file")

            if [[ "$new_encoding" == *"utf-8"* ]] || [[ "$new_encoding" == *"UTF-8"* ]]; then
                converted_files=$((converted_files + 1))
                echo "  ✅ 转换成功: $utf8_file"
                echo "  编码: $from_encoding → UTF-8"

                {
                    echo "状态: ✅ 已转换"
                    echo "输出: $utf8_file"
                    echo "新编码: $new_encoding"
                } >> "$REPORT_FILE"
            else
                failed_files=$((failed_files + 1))
                echo "  ❌ 转换失败: 验证失败"
                rm -f "$utf8_file"

                {
                    echo "状态: ❌ 转换失败（验证失败）"
                } >> "$REPORT_FILE"
            fi
        else
            failed_files=$((failed_files + 1))
            echo "  ❌ 转换失败: iconv错误"

            {
                echo "状态: ❌ 转换失败（iconv错误）"
            } >> "$REPORT_FILE"
        fi
    else
        echo "  ✅ 编码正常"

        {
            echo "状态: ✅ 编码正常"
        } >> "$REPORT_FILE"
    fi

    echo "" >> "$REPORT_FILE"
    echo ""
done

# 输出统计
echo "========================================="
echo "转换完成"
echo "========================================="
echo "总文件数: $total_files"
echo "UTF-16文件: $utf16_files"
echo "成功转换: $converted_files"
echo "转换失败: $failed_files"
echo ""
echo "详细报告已保存到: $REPORT_FILE"
echo ""

# 提示后续操作
if [ $converted_files -gt 0 ]; then
    echo "⚠️  请注意:"
    echo "1. 已生成 *_utf8.xml 文件"
    echo "2. 需要更新对应的 JSON 配置文件"
    echo "3. 原始UTF-16文件未删除，请手动确认后删除"
    echo ""
    echo "批量更新配置命令（仅供参考）:"
    echo "find src/main/resources/CONF -name '*.json' -exec sed -i 's/\\.xml/_utf8.xml/g' {} +"
fi

echo "========================================="
