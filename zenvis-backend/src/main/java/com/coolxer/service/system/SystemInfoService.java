package com.coolxer.service.system;

import com.coolxer.model.system.dto.SystemInfoDto;
import com.coolxer.model.system.vo.SystemInfoVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统信息服务接口
 */
public interface SystemInfoService {

    public static final String SYSTEM_ICON_FILENAME = "logo.ico";
    public static final String SYSTEM_BANNER_FILENAME = "banner.png";
    public static final String SYSTEM_LOGO_FILENAME = "logo.png";

    /**
     * 获取系统信息
     *
     * @return 系统信息
     */
    SystemInfoVo getSystemInfo();

    /**
     * 保存系统信息
     *
     * @param systemInfoDto 系统信息DTO
     * @return 是否成功
     */
    boolean save(SystemInfoDto systemInfoDto);

    /**
     * 更新系统信息
     *
     * @param systemInfoDto 系统信息DTO
     * @return 是否成功
     */
    boolean update(SystemInfoDto systemInfoDto);

    /**
     * 上传系统图标或banner 图
     *
     * @param file 文件
     * @return 是否成功
     */
    boolean update(MultipartFile file, String fileName);

}
