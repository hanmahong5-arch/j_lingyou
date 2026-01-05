package red.jiuzhou.langchain.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 游戏内容检索器
 *
 * <p>实现 LangChain4j 的 ContentRetriever 接口，用于 RAG 检索。
 *
 * <p>功能：
 * <ul>
 *   <li>语义搜索 - 根据用户查询检索相关内容</li>
 *   <li>相似度过滤 - 只返回相似度超过阈值的结果</li>
 *   <li>结果限制 - 限制返回的最大结果数</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Component
@ConditionalOnBean({EmbeddingStore.class, EmbeddingModel.class})
public class GameContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(GameContentRetriever.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    /** 最大返回结果数 */
    private int maxResults = 5;

    /** 最小相似度阈值 */
    private double minScore = 0.5;

    public GameContentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        log.info("GameContentRetriever 初始化完成，maxResults={}, minScore={}", maxResults, minScore);
    }

    @Override
    public List<Content> retrieve(Query query) {
        String queryText = query.text();
        log.debug("检索查询: {}", queryText);

        try {
            // 生成查询嵌入
            var queryEmbedding = embeddingModel.embed(queryText).content();

            // 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            // 执行搜索
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

            // 转换为 Content 列表
            List<Content> contents = searchResult.matches().stream()
                    .map(this::toContent)
                    .collect(Collectors.toList());

            log.debug("检索到 {} 条相关内容", contents.size());

            if (log.isTraceEnabled()) {
                for (int i = 0; i < contents.size(); i++) {
                    Content c = contents.get(i);
                    EmbeddingMatch<TextSegment> match = searchResult.matches().get(i);
                    log.trace("  [{}] score={}: {}", i, match.score(),
                            truncate(c.textSegment().text(), 50));
                }
            }

            return contents;

        } catch (Exception e) {
            log.error("内容检索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 将 EmbeddingMatch 转换为 Content
     */
    private Content toContent(EmbeddingMatch<TextSegment> match) {
        return Content.from(match.embedded());
    }

    /**
     * 截断字符串
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ==================== 配置方法 ====================

    /**
     * 设置最大返回结果数
     */
    public GameContentRetriever withMaxResults(int maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    /**
     * 设置最小相似度阈值
     */
    public GameContentRetriever withMinScore(double minScore) {
        this.minScore = minScore;
        return this;
    }

    // ==================== Getter/Setter ====================

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
