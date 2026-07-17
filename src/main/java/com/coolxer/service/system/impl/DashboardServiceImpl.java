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
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 看板接口实现
 */
@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

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
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public Dashboard create(DashboardDto dashboardDto) {
        checkCreateOrUpdate(dashboardDto);
        List<Dashboard> dashboards = dashboardRepository.findAllForUpdate();
        repairDefaultDashboards(dashboards);

        Dashboard dashboard = new Dashboard();
        dashboard.updateFromDto(dashboardDto);
        if (Boolean.TRUE.equals(dashboardDto.getIsDefault()) || findDefaultDashboards(dashboards).isEmpty()) {
            dashboards.forEach(item -> item.setIsDefault(false));
            dashboard.setIsDefault(true);
        } else {
            dashboard.setIsDefault(false);
        }
        dashboardRepository.saveAll(dashboards);
        return dashboardRepository.save(dashboard);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public Boolean update(Long id, DashboardDto dashboardDto) {
        checkCreateOrUpdate(dashboardDto);
        List<Dashboard> dashboards = dashboardRepository.findAllForUpdate();
        Dashboard dashboard = findById(dashboards, id);
        if (dashboard == null) {
            return false;
        }

        repairDefaultDashboards(dashboards);
        applyDefaultChange(dashboards, dashboard, dashboardDto.getIsDefault());
        dashboard.updateFromDto(dashboardDto);
        dashboardRepository.saveAll(dashboards);
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public Boolean bulkUpdate(List<Long> ids, DashboardDto dashboardDto) {
        checkCreateOrUpdate(dashboardDto);
        Set<Long> uniqueIds = normalizeIds(ids);
        if (Boolean.TRUE.equals(dashboardDto.getIsDefault()) && uniqueIds.size() > 1) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_MULTIPLE_DEFAULT_NOT_ALLOWED);
        }
        if (uniqueIds.isEmpty()) {
            return true;
        }

        List<Dashboard> dashboards = dashboardRepository.findAllForUpdate();
        List<Dashboard> targets = dashboards.stream()
                .filter(item -> uniqueIds.contains(item.getId().longValue()))
                .toList();
        if (targets.size() != uniqueIds.size()) {
            return false;
        }

        repairDefaultDashboards(dashboards);
        if (Boolean.FALSE.equals(dashboardDto.getIsDefault())
                && targets.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()))) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_REQUIRED);
        }
        if (Boolean.TRUE.equals(dashboardDto.getIsDefault())) {
            dashboards.forEach(item -> item.setIsDefault(false));
        }
        targets.forEach(item -> item.updateFromDto(dashboardDto));
        dashboardRepository.saveAll(dashboards);
        return true;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public void delete(Long id) {
        List<Dashboard> dashboards = dashboardRepository.findAllForUpdate();
        Dashboard dashboard = findById(dashboards, id);
        if (dashboard == null) {
            return;
        }
        repairDefaultDashboards(dashboards);
        assertNotDefaultDashboard(dashboard);
        dashboardRepository.saveAll(dashboards);
        dashboardRepository.delete(dashboard);
    }


    @Override
    @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        Set<Long> uniqueIds = normalizeIds(ids);
        if (uniqueIds.isEmpty()) {
            return;
        }
        List<Dashboard> dashboards = dashboardRepository.findAllForUpdate();
        List<Dashboard> targets = dashboards.stream()
                .filter(item -> uniqueIds.contains(item.getId().longValue()))
                .toList();
        repairDefaultDashboards(dashboards);
        if (targets.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()))) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED);
        }
        dashboardRepository.saveAll(dashboards);
        dashboardRepository.deleteAll(targets);
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

    private static void applyDefaultChange(List<Dashboard> dashboards,
                                           Dashboard dashboard,
                                           Boolean requestedDefault) {
        if (Boolean.FALSE.equals(requestedDefault) && Boolean.TRUE.equals(dashboard.getIsDefault())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_REQUIRED);
        }
        if (Boolean.TRUE.equals(requestedDefault)) {
            dashboards.forEach(item -> item.setIsDefault(false));
        }
    }

    private static void assertNotDefaultDashboard(Dashboard dashboard) {
        if (Boolean.TRUE.equals(dashboard.getIsDefault())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED);
        }
    }

    private static Dashboard findById(List<Dashboard> dashboards, Long id) {
        if (id == null) {
            return null;
        }
        return dashboards.stream()
                .filter(item -> item.getId() != null && item.getId().longValue() == id)
                .findFirst()
                .orElse(null);
    }

    private static Set<Long> normalizeIds(List<Long> ids) {
        Set<Long> uniqueIds = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(java.util.Objects::nonNull).forEach(uniqueIds::add);
        }
        return uniqueIds;
    }

    private static List<Dashboard> findDefaultDashboards(List<Dashboard> dashboards) {
        if (dashboards == null) {
            return Collections.emptyList();
        }
        return dashboards.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
                .toList();
    }

    private static void repairDefaultDashboards(List<Dashboard> dashboards) {
        List<Dashboard> defaults = new ArrayList<>(findDefaultDashboards(dashboards));
        if (defaults.size() == 1 || dashboards == null || dashboards.isEmpty()) {
            return;
        }
        Dashboard retained = dashboards.stream()
                .filter(item -> "system-board".equals(item.getCode()))
                .findFirst()
                .orElseGet(() -> (defaults.isEmpty() ? dashboards.stream() : defaults.stream())
                        .min(Comparator.comparing(Dashboard::getId, Comparator.nullsLast(Integer::compareTo)))
                        .orElse(dashboards.get(0)));
        dashboards.forEach(item -> item.setIsDefault(item == retained));
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
        if (dashboardDto.getType() == DashboardType.HTML_PAGE) {
            dashboardDto.setHtmlPath(normalizeHtmlPath(dashboardDto.getHtmlPath()));
        }
        if (dashboardDto.getType() == DashboardType.LINK && StringUtils.isBlank(dashboardDto.getUrl())) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
    }

    private static String normalizeHtmlPath(String htmlPath) {
        String candidate = StringUtils.trimToEmpty(htmlPath);
        if (candidate.isEmpty()) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR);
        }
        if (candidate.startsWith("/") || candidate.startsWith("//")
                || candidate.contains("\\") || candidate.contains("\0")
                || candidate.contains("?") || candidate.contains("#")
                || URI_SCHEME_PATTERN.matcher(candidate).find()) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_HTML_PATH_INVALID);
        }
        String[] parts = candidate.split("/", -1);
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new ApiException(ResultCodeEnum.DASHBOARD_HTML_PATH_INVALID);
            }
        }
        try {
            Path normalized = Path.of(candidate).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..") || normalized.toString().isBlank()) {
                throw new ApiException(ResultCodeEnum.DASHBOARD_HTML_PATH_INVALID);
            }
        } catch (InvalidPathException e) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_HTML_PATH_INVALID);
        }
        return candidate;
    }

}
