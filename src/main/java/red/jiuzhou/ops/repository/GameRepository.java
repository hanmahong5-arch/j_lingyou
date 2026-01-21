package red.jiuzhou.ops.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游戏数据仓储接口
 *
 * 抽象数据访问层，支持：
 * - 多数据源切换
 * - 缓存集成
 * - 批量操作
 * - 事务管理
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 *
 * @author yanxq
 * @date 2026-01-16
 */
public interface GameRepository<T, ID> {

    // ==================== 查询操作 ====================

    /**
     * 根据ID查询
     */
    Optional<T> findById(ID id);

    /**
     * 根据名称查询
     */
    Optional<T> findByName(String name);

    /**
     * 查询所有（分页）
     */
    List<T> findAll(int offset, int limit);

    /**
     * 条件查询
     */
    List<T> findByCondition(Map<String, Object> conditions, int offset, int limit);

    /**
     * 搜索（模糊匹配）
     */
    List<T> search(String keyword, int limit);

    /**
     * 统计数量
     */
    long count();

    /**
     * 条件统计
     */
    long countByCondition(Map<String, Object> conditions);

    /**
     * 检查是否存在
     */
    boolean exists(ID id);

    // ==================== 修改操作 ====================

    /**
     * 保存（新增或更新）
     */
    T save(T entity);

    /**
     * 批量保存
     */
    List<T> saveAll(List<T> entities);

    /**
     * 更新指定字段
     */
    boolean update(ID id, Map<String, Object> fields);

    /**
     * 删除
     */
    boolean delete(ID id);

    /**
     * 批量删除
     */
    int deleteAll(List<ID> ids);

    // ==================== 批量操作 ====================

    /**
     * 批量查询
     */
    List<T> findByIds(List<ID> ids);

    /**
     * 批量更新
     */
    int batchUpdate(Map<String, Object> fields, Map<String, Object> conditions);

    // ==================== 原生查询 ====================

    /**
     * 执行原生SQL查询
     */
    List<Map<String, Object>> executeQuery(String sql, Object... params);

    /**
     * 执行原生SQL更新
     */
    int executeUpdate(String sql, Object... params);

    /**
     * 调用存储过程
     */
    List<Map<String, Object>> callProcedure(String procedureName, Map<String, Object> params);
}
