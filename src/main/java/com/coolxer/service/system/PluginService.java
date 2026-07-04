package com.coolxer.service.system;

import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
import com.coolxer.model.system.vo.PluginVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 插件接口
 */
public interface PluginService {

    /**
     * 查询全部列表
     *
     * @return 结果
     */
    List<PluginVo> findAll();

    /**
     * 创建插件
     *
     * @param pluginDto 传输实体
     */
    Plugin create(PluginDto pluginDto);

    /**
     * 修改插件
     *
     * @param id        插件id
     * @param pluginDto 用户传输实体
     */
    Boolean update(Long id, PluginDto pluginDto);

    /**
     * 删除插件
     *
     * @param id 插件id
     */
    void delete(Long id);

    /**
     * 批量删除
     */
    void deleteByIds(List<Long> ids);

    /**
     * 插件详情
     *
     * @param id 插件id
     * @return 结果
     */
    PluginVo info(Long id);

    /**
     * 获取插件列表
     *
     * @param pluginSearchDto 搜索参数
     * @return 插件列表
     */
    PageRowsVo<PluginVo> getPageList(PluginSearchDto pluginSearchDto);

    /**
     * 上传文件
     *
     * @param file
     * @return
     */
    PluginVo uploadFile(MultipartFile file);

    /**
     * icon转base64
     *
     * @param file
     * @return
     */
    String base64Icon(MultipartFile file);

    /**
     * 导出插件
     *
     * @param id 插件id
     * @return
     */
    void export(Long id, HttpServletResponse response);

    /**
     * 卸载插件
     *
     * @param id
     * @return
     */
    PluginVo uninstall(Long id);

    /**
     * 安装插件
     *
     * @param id
     * @return
     */
    PluginVo install(Long id);

    /**
     * 获取插件readme
     *
     * @param id
     * @return
     */
    String readme(Long id);

    /**
     * 判断包名是否存在
     *
     * @param packageName
     * @return
     */
    boolean isPackageExist(String packageName);

    /**
     * 获取文件树
     *
     * @param id
     * @return
     */
    List<FileTreeNodeVo> docTree(Long id);

    /**
     * 读取文件
     *
     * @param id
     * @param file
     * @return
     */
    String readDocFile(Long id, String file);

    /**
     * 获取插件日志
     *
     * @param id
     * @return
     */
    String getLogs(Long id);
}
