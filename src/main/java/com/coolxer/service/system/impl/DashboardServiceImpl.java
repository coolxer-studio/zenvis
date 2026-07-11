package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.DashboardSearchDto;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.service.system.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 看板接口实现
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Override
    public List<DashboardVo> findAll() {
        return dashboardRepository.findAll().stream().map(DashboardVo::new).toList();
    }

    @Override
    public PageRowsVo<DashboardVo> getPageList(DashboardSearchDto dashboardSearchDto) {
        try {
            Pageable pageable = PageRequest.of(dashboardSearchDto.getPage() - 1, dashboardSearchDto.getPerPage());
            Page<Dashboard> byPage;
            byPage = dashboardRepository.findByPage(pageable, dashboardSearchDto.getName(), dashboardSearchDto.getUrl());
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(DashboardVo::new).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public Dashboard create(DashboardDto dashboardDto) {
        checkCreateOrUpdate(dashboardDto);
        Dashboard dashboard = new Dashboard();
        dashboard.updateFromDto(dashboardDto);
        return dashboardRepository.save(dashboard);
    }

    @Override
    public Boolean update(Long id, DashboardDto dashboardDto) {
        checkCreateOrUpdate(dashboardDto);
        try {
            Optional<Dashboard> optionalDashboard = dashboardRepository.findById(id);
            if (optionalDashboard.isPresent()) {
                Dashboard dashboard = optionalDashboard.get();
                dashboard.updateFromDto(dashboardDto);
                dashboardRepository.save(dashboard);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id) {
        dashboardRepository.deleteById(id);
    }


    @Override
    public void deleteByIds(List<Long> ids) {
        dashboardRepository.deleteAllById(ids);
    }

    @Override
    public DashboardVo info(Long id) {
        try {
            Optional<Dashboard> optionalDashboard = dashboardRepository.findById(id);
            return optionalDashboard.map(DashboardVo::new).orElse(null);
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    private static void checkCreateOrUpdate(DashboardDto dashboardDto) {
        if (StringUtils.isBlank(dashboardDto.getName()) || dashboardDto.getType() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (dashboardDto.getType() == DashboardType.BUILT && StringUtils.isBlank(dashboardDto.getCode())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
        if (dashboardDto.getType() == DashboardType.LOW_CODE_PAGE && StringUtils.isBlank(dashboardDto.getConfigIndex())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
        if (dashboardDto.getType() == DashboardType.HTML_PAGE && StringUtils.isBlank(dashboardDto.getHtmlPath())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
        if (dashboardDto.getType() == DashboardType.LINK && StringUtils.isBlank(dashboardDto.getUrl())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
    }

}
