package com.coolxer.service.retrieval;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.query.IpEventTimelineQueryRequest;
import com.coolxer.model.retrieval.query.IpRelationQueryRequest;

import java.util.List;
import java.util.Map;

/**
 * 实体核心服务提供所有实体常用接口，增删改查等基础操作
 */
public interface EntityCoreService {

    /**
     * 新增
     *
     * @param entityName 实体名称
     * @param mapDto     通用DTO
     * @return 是否新增成功
     */
    boolean add(String entityName, Map<String, Object> mapDto);

    /**
     * 删除
     *
     * @param entityName 实体名称
     * @param id         平台记录ID（zenvis_id UUID）
     * @return 是否删除成功
     */
    boolean delete(String entityName, String id);

    /**
     * 批量删除
     *
     * @param entityName 实体名称
     * @param ids        平台记录ID列表（zenvis_id UUID）
     * @return 是否删除成功
     */
    boolean deleteALL(String entityName, List<String> ids);

    /**
     * 更新信息
     *
     * @param entityName 实体名称
     * @param id         平台记录ID（zenvis_id UUID）
     * @param mapDto     通用DTO
     * @return 是否更新成功
     */
    boolean update(String entityName, String id, Map<String, Object> mapDto);

    /**
     * 批量更新信息。执行任何更新前会先校验全部平台记录ID。
     *
     * @param entityName 实体名称
     * @param ids        平台记录ID列表（zenvis_id UUID）
     * @param mapDto     通用DTO
     * @return 是否更新成功
     */
    boolean updateALL(String entityName, List<String> ids, Map<String, Object> mapDto);

    /**
     * 根据ID查询
     *
     * @param entityName 实体名称
     * @param id         平台记录ID（zenvis_id UUID）
     * @return 详细信息
     */
    Map<String, Object> getOne(String entityName, String id);

    /**
     * 分页查询资产主机列表
     *
     * @param entityName   实体名称
     * @param searchMapDto 查询条件
     * @return 分页结果
     */
    PageRowsVo<Map<String, Object>> getPageList(String entityName, Map<String, Object> searchMapDto);

    /**
     * 获取属性的映射关系
     *
     * @param entityName 实体名称
     * @param attribute  属性名称
     * @return 映射关系
     */
    Map<String, Object> getAttributeMapping(String entityName, String attribute);

    /**
     * 获取所有不重复的属性列表
     *
     * @param entityName 实体名称
     * @param attribute  属性名称
     * @return 列表
     */
    List<String> getDistinctAttributes(String entityName, String attribute);

    /**
     * 获取相似的属性列表
     *
     * @param entityName 实体名称
     * @param attribute  属性名称
     * @param term       搜索词
     * @return 列表
     */
    List<String> getSimilarAttributes(String entityName, String attribute, String term);


    /**
     * 统计总量
     *
     * @param entityName   实体名称
     * @param searchMapDto 查询条件
     * @return 数量
     */
    long countTotal(String entityName, Map<String, Object> searchMapDto);


    Map<String, Object> count(List<String> entities);

    Map<String, Object> countToady(List<String> entities);

    Map<String, Object> trend(List<String> entities);

    Map<String, Object> statistics(List<String> entities, String field);

    /**
     * 按 IP 统计多个实体中源、目的 IP 字段匹配的数据量。
     *
     * @param entities 实体名称列表
     * @param ip       待统计的 IP
     * @return 跨实体统计结果
     */
    Map<String, Object> ipStatistics(List<String> entities, String ip);

    /**
     * 按显式 Meta 逻辑字段映射查询指定时间范围内的真实对端 IP。
     */
    Map<String, Object> ipRelations(IpRelationQueryRequest request);

    /**
     * 按显式 Meta 逻辑字段映射聚合指定 IP 的安全事件时间轴。
     */
    Map<String, Object> ipEventTimeline(IpEventTimelineQueryRequest request);
}
