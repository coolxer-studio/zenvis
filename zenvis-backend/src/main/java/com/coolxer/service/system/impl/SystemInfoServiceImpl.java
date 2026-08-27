package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.SystemInfo;
import com.coolxer.dao.mysql.repository.SystemInfoRepository;
import com.coolxer.model.system.dto.SystemInfoDto;
import com.coolxer.model.system.vo.SystemInfoVo;
import com.coolxer.service.system.SystemInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 系统信息服务实现
 */
@Slf4j
@Service
public class SystemInfoServiceImpl implements SystemInfoService {

    @Autowired
    private SystemInfoRepository systemInfoRepository;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Override
    public SystemInfoVo getSystemInfo() {
        try {
            // 获取第一条记录（通常只有一条系统信息记录）
            Optional<SystemInfo> systemInfoOptional = systemInfoRepository.findById(1);
            if (systemInfoOptional.isPresent()) {
                SystemInfo systemInfo = systemInfoOptional.get();
                SystemInfoVo vo = new SystemInfoVo();
                BeanUtils.copyProperties(systemInfo, vo);
                return vo;
            }
            // 如果没有数据，返回空对象
            return new SystemInfoVo();
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            return new SystemInfoVo();
        }
    }

    @Override
    public boolean save(SystemInfoDto systemInfoDto) {
        try {
            // 检查是否已存在记录
            Optional<SystemInfo> systemInfoOptional = systemInfoRepository.findById(1);
            if (systemInfoOptional.isPresent()) {
                log.warn("系统信息已存在，无法重复保存");
                return false;
            }

            // 创建新记录
            SystemInfo systemInfo = new SystemInfo();
            systemInfo.setId(1);
            BeanUtils.copyProperties(systemInfoDto, systemInfo);
            systemInfoRepository.save(systemInfo);
            return true;
        } catch (Exception e) {
            log.error("保存系统信息失败", e);
            return false;
        }
    }

    @Override
    public boolean update(SystemInfoDto systemInfoDto) {
        try {
            // 尝试获取现有记录
            Optional<SystemInfo> systemInfoOptional = systemInfoRepository.findById(1);
            if (!systemInfoOptional.isPresent()) {
                log.warn("系统信息不存在，无法更新");
                return false;
            }

            // 更新现有记录 - 只更新有值的字段
            SystemInfo systemInfo = systemInfoOptional.get();
            if (systemInfoDto.getSystemTitle() != null) {
                systemInfo.setSystemTitle(systemInfoDto.getSystemTitle());
            }
            if (systemInfoDto.getSystemSubtitle() != null) {
                systemInfo.setSystemSubtitle(systemInfoDto.getSystemSubtitle());
            }
            if (systemInfoDto.getProductName() != null) {
                systemInfo.setProductName(systemInfoDto.getProductName());
            }
            if (systemInfoDto.getProductVersion() != null) {
                systemInfo.setProductVersion(systemInfoDto.getProductVersion());
            }
            if (systemInfoDto.getProductIntroduction() != null) {
                systemInfo.setProductIntroduction(systemInfoDto.getProductIntroduction());
            }
            if (systemInfoDto.getServicePhone() != null) {
                systemInfo.setServicePhone(systemInfoDto.getServicePhone());
            }
            if (systemInfoDto.getServiceEmail() != null) {
                systemInfo.setServiceEmail(systemInfoDto.getServiceEmail());
            }
            if (systemInfoDto.getTechnicalEmail() != null) {
                systemInfo.setTechnicalEmail(systemInfoDto.getTechnicalEmail());
            }
            if (systemInfoDto.getIntegrateLink() != null) {
                systemInfo.setIntegrateLink(systemInfoDto.getIntegrateLink());
            }
            if (systemInfoDto.getCopyright() != null) {
                systemInfo.setCopyright(systemInfoDto.getCopyright());
            }

            systemInfoRepository.save(systemInfo);
            return true;
        } catch (Exception e) {
            log.error("更新系统信息失败", e);
            return false;
        }
    }

    @Override
    public boolean update(MultipartFile file, String fileName) {
        if (file.isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        // 获取原始文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID);
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        if (!fileName.endsWith(extension)) {
            throw new ApiException(ResultCodeEnum.FILE_NAME_INVALID);
        }
        // 确保目录存在
        File directory = new File(customWebConfig.getSystemConfigPath());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        // 保存文件到sysInfoPath路径下
        Path filePath = Paths.get(customWebConfig.getSystemConfigPath(), fileName);
        try {
            Files.write(filePath, file.getBytes());
        } catch (IOException e) {
            throw new ApiException(ResultCodeEnum.FILE_WRITE_FAIL);
        }
        return true;
    }

}
