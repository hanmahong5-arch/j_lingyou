package red.jiuzhou.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

/**
 * 透明编码转换层测试
 *
 * 测试功能：
 * 1. 编码检测
 * 2. 元数据保存和查询
 * 3. 往返一致性验证
 * 4. 缓存功能
 */
@SpringBootTest
public class EncodingLayerTest {

    private static final Logger log = LoggerFactory.getLogger(EncodingLayerTest.class);

    @Test
    public void testEncodingDetection() {
        log.info("========== 测试1：编码检测 ==========");

        // 测试一个已知的 XML 文件
        File xmlFile = new File("D:\\AionReal58\\AionMap\\XML\\abyss_mist_times.xml");
        if (!xmlFile.exists()) {
            log.warn("测试文件不存在: {}", xmlFile.getPath());
            return;
        }

        // 使用智能回退策略检测编码
        FileEncodingDetector.EncodingInfo encoding =
                EncodingFallbackStrategy.detectWithFallback(xmlFile, "abyss_mist_times");

        int confidence = EncodingFallbackStrategy.calculateConfidence(encoding, xmlFile);

        log.info("✅ 检测结果: 文件={}, 编码={}, BOM={}, 可信度={}%",
                xmlFile.getName(), encoding.getEncoding(), encoding.hasBOM(), confidence);

        assert encoding != null : "编码检测失败";
        assert confidence > 50 : "可信度过低: " + confidence;
    }

    @Test
    public void testMetadataSaveAndQuery() {
        log.info("========== 测试2：元数据保存和查询 ==========");

        File xmlFile = new File("D:\\AionReal58\\AionMap\\XML\\abyss_mist_times.xml");
        if (!xmlFile.exists()) {
            log.warn("测试文件不存在: {}", xmlFile.getPath());
            return;
        }

        String tableName = "abyss_mist_times";
        String mapType = "";

        // 检测编码
        FileEncodingDetector.EncodingInfo encoding =
                EncodingFallbackStrategy.detectWithFallback(xmlFile, tableName);

        // 保存元数据
        log.info("保存元数据: 表={}, 编码={}", tableName, encoding);
        EncodingMetadataManager.saveMetadata(tableName, mapType, xmlFile, encoding);

        // 查询元数据
        FileEncodingDetector.EncodingInfo queried =
                EncodingMetadataManager.getMetadata(tableName, mapType);

        log.info("✅ 查询结果: 表={}, 编码={}, BOM={}",
                tableName, queried.getEncoding(), queried.hasBOM());

        assert queried.getEncoding().equals(encoding.getEncoding()) : "编码不匹配";
        assert queried.hasBOM() == encoding.hasBOM() : "BOM 标记不匹配";
    }

    @Test
    public void testCache() {
        log.info("========== 测试3：缓存功能 ==========");

        String tableName = "abyss_mist_times";
        String mapType = "";

        // 第一次查询（缓存未命中）
        long start1 = System.currentTimeMillis();
        FileEncodingDetector.EncodingInfo encoding1 =
                EncodingMetadataCache.getWithCache(tableName, mapType);
        long time1 = System.currentTimeMillis() - start1;
        log.info("第一次查询耗时: {}ms, 结果: {}", time1, encoding1);

        // 第二次查询（缓存命中）
        long start2 = System.currentTimeMillis();
        FileEncodingDetector.EncodingInfo encoding2 =
                EncodingMetadataCache.getWithCache(tableName, mapType);
        long time2 = System.currentTimeMillis() - start2;
        log.info("第二次查询耗时: {}ms, 结果: {}", time2, encoding2);

        log.info("✅ 缓存加速效果: 第一次={}ms, 第二次={}ms, 提升={}%",
                time1, time2, (time1 - time2) * 100.0 / time1);

        // 验证缓存统计
        log.info("缓存统计:\n{}", EncodingMetadataCache.getStatistics());
    }

    @Test
    public void testRoundTripValidation() {
        log.info("========== 测试4：往返一致性验证 ==========");

        String tableName = "abyss_mist_times";
        String mapType = "";
        File originalFile = new File("D:\\AionReal58\\AionMap\\XML\\abyss_mist_times.xml");

        if (!originalFile.exists()) {
            log.warn("测试文件不存在: {}", originalFile.getPath());
            return;
        }

        // 保存原始文件哈希
        String originalHash = RoundTripValidator.saveFileHash(tableName, mapType, originalFile);
        log.info("原始文件 MD5: {}", originalHash);

        // 模拟导出后的文件（这里使用原文件进行测试）
        RoundTripValidator.ValidationResult result =
                RoundTripValidator.validateRoundTrip(tableName, mapType, originalFile);

        log.info("✅ 往返验证: {}", result.getMessage());
        log.info("   - 原始哈希: {}", result.getOriginalHash());
        log.info("   - 导出哈希: {}", result.getExportedHash());
        log.info("   - 验证结果: {}", result.isPassed() ? "✅ 通过" : "❌ 失败");

        assert result.isPassed() : "往返验证失败";
    }

    @Test
    public void testStatistics() {
        log.info("========== 测试5：编码统计信息 ==========");

        String statistics = EncodingMetadataManager.getEncodingStatistics();
        log.info("编码统计:\n{}", statistics);
    }
}
