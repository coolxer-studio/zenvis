package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.vo.DashboardVo;

import java.util.List;

/**
 * 看板接口
 */
public interface DashboardService {

    /**
     * 查询全部列表
     *
     * @return 结果
     */
    List<DashboardVo> findAll();

    /**
     * 创建看板
     *
     * @param dashboardDto 传输实体
     */
    Dashboard create(DashboardDto dashboardDto);

    /**
     * 修改看板
     *
     * @param id           看板id
     * @param dashboardDto 用户传输实体
     */
    Boolean update(Long id, DashboardDto dashboardDto);

    /**
     * 批量修改看板
     *
     * @param ids          看板id列表
     * @param dashboardDto 用户传输实体
     */
    Boolean bulkUpdate(List<Long> ids, DashboardDto dashboardDto);

    /**
     * 删除看板
     *
     * @param id 看板id
     */
    void delete(Long id);

    /**
     * 批量删除
     */
    void deleteByIds(List<Long> ids);

    /**
     * 看板详情
     *
     * @param id 看板id
     * @return 结果
     */
    DashboardVo info(Long id);

    /**
     * 获取看板列表
     *
     * @param dashboardSearchDto 搜索参数
     * @return 看板列表
     */
    PageRowsVo<DashboardVo> getPageList(DashboardSearchDto dashboardSearchDto);


}
