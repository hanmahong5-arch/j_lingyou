package red.jiuzhou.langchain.tools;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.langchain.LangChainModelFactory;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.SpringContextHolder;
import red.jiuzhou.util.YamlUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LangChain4j 批量处理助手
 *
 * <p>替代原有的 DashScopeBatchHelper，使用 LangChain4j 框架进行批量 AI 处理。
 * 提供：
 * <ul>
 *   <li>批量文本处理 - 将多个文本合并为一次 AI 调用</li>
 *   <li>虚拟线程并行 - Java 21+ 虚拟线程高效处理</li>
 *   <li>自动重试 - 失败自动重试，带指数退避</li>
 *   <li>结果缓存 - 缓存 AI 响应避免重复调用</li>
 * </ul>
 *
 * @author Claude
 * @version 2.0 (LangChain4j)
 */
public class LangChainBatchHelper {
    private static final Logger log = LoggerFactory.getLogger(LangChainBatchHelper.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 3;
    private static final String DELIMITER = "!@#";

    // AI 调用缓存，key 为 prompt 内容，value 为响应结果
    private static final ConcurrentHashMap<String, String> aiResponseCache = new ConcurrentHashMap<>();

    /**
     * 构建批量提示词
     *
     * @param inputs    输入文本列表
     * @param promptKey 提示词配置键
     * @return 完整的批量提示词
     */
    public static String buildBatchPrompt(List<String> inputs, String promptKey) {
        String property = Optional.ofNullable(YamlUtils.getProperty("ai.promptKey." + promptKey)).orElse("");
        return property + "：\n" + String.join(DELIMITER, inputs);
    }

    /**
     * 解析批量结果
     *
     * @param response      AI 响应
     * @param expectedCount 期望的结果数量
     * @return 解析后的结果列表
     */
    public static List<String> parseBatchResult(String response, int expectedCount) {
        String[] parts = response.split(Pattern.quote(DELIMITER));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            result.add(i < parts.length ? parts[i].trim() : "");
        }
        return result;
    }

    /**
     * 批量改写字段
     *
     * @param dataList    数据列表
     * @param tabName     表名
     * @param fieldName   字段名
     * @param aiModelName AI 模型名称
     */
    public static void rewriteField(List<Map<String, String>> dataList, String tabName,
                                    String fieldName, String aiModelName) {
        // 获取 LangChain4j 模型工厂
        LangChainModelFactory modelFactory = SpringContextHolder.getBean(LangChainModelFactory.class);

        // 使用虚拟线程（Java 21+）
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int start = 0; start < dataList.size(); start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE, dataList.size());
                List<Map<String, String>> subList = dataList.subList(start, end);

                int finalStart = start;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        List<String> originalTexts = subList.stream()
                                .map(m -> m.getOrDefault(fieldName, ""))
                                .collect(Collectors.toList());

                        String promptKey = tabName + "@" + fieldName;
                        String prompt = buildBatchPrompt(originalTexts, promptKey);

                        // 尝试使用缓存
                        String response = aiResponseCache.computeIfAbsent(prompt, p -> {
                            for (int i = 1; i <= MAX_RETRY; i++) {
                                try {
                                    long startTime = System.currentTimeMillis();
                                    // 使用 LangChain4j 调用 AI
                                    ChatLanguageModel chatModel = modelFactory.getModel(aiModelName);
                                    String res = chatModel.generate(p);
                                    log.info("调用 AI (LangChain4j) 耗时 {}ms", System.currentTimeMillis() - startTime);
                                    if (res != null && !res.isEmpty()) {
                                        return res;
                                    } else {
                                        log.error("第 {} 次调用 AI 返回为空，重试中...", i);
                                    }
                                } catch (Exception ex) {
                                    log.error("第 {} 次调用 AI 错误：{}", i, ex.getMessage());
                                    log.error("批次 {} ~ {} 批次异常：{}", finalStart, end - 1, JSONRecord.getErrorMsg(ex));
                                }
                                try {
                                    Thread.sleep(1000L * i); // 增加退避时间
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            }

                            log.error("多次调用 AI 失败，prompt 被跳过：{}", p);
                            return ""; // 最终失败也要写缓存，避免再次触发
                        });

                        if (!StringUtils.hasLength(response)) {
                            log.error("AI 批次最终返回为空，跳过批次：{} ~ {}", finalStart, end - 1);
                            return;
                        }

                        List<String> newTexts = parseBatchResult(response, subList.size());
                        for (int i = 0; i < subList.size(); i++) {
                            subList.get(i).put(fieldName, newTexts.get(i));
                        }

                    } catch (Exception e) {
                        log.error("处理批次 {} ~ {} 批次异常：{}", finalStart, end - 1, e.getMessage());
                        log.error("批次 {} ~ {} 批次异常：{}", finalStart, end - 1, JSONRecord.getErrorMsg(e));
                    }
                }, executor);

                futures.add(future);
            }

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } // try-with-resources 自动关闭 executor
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        aiResponseCache.clear();
        log.info("LangChainBatchHelper 缓存已清除");
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return aiResponseCache.size();
    }
}
