package com.coolxer.service.config;

import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigVo;

import java.util.List;

/**
 * 配置文件接口
 */
public interface ConfigService {

    /**
     * 树装结果信息
     *
     * @param type
     * @return
     */
    List<ConfigVo> getConfigFileTree(String type);

    /**
     * 获取文件schema
     *
     * @param type
     * @param fileName
     * @return
     */
    String readFileSchema(String type, String fileName);

    /**
     * 读取文件
     *
     * @param type
     * @param fileName 文件名
     * @return 文件内容
     */
    String readFile(String type, String fileName);

    /**
     * 修改文件内存
     *
     * @param type
     * @param configDto 配置对象实体
     */
    void modifyConfig(String type, ConfigDto configDto);

    /**
     * 添加文件
     *
     * @param type
     * @param fileName
     * @return
     */
    boolean addFile(String type, String fileName);

    /**
     * 重命名文件
     *
     * @param type
     * @param originalFile
     * @param newFile
     * @return
     */
    boolean renameFile(String type, String originalFile, String newFile);

    /**
     * 删除文件
     *
     * @param type
     * @param fileName
     * @return
     */
    boolean deleteFile(String type, String fileName);

    /**
     * 获取配置文件路径
     *
     * @param type
     * @return
     */
    public String configPath(String type);

    /**
     * 应用配置
     *
     * @param type
     * @param configDto
     */
    public void applyConfig(String type, ConfigDto configDto);

    /**
     * 添加根路径
     *
     * @param type
     */
    boolean addRootPath(String type);

    /**
     * 幂等创建配置根路径
     *
     * @param type
     */
    boolean ensureRootPath(String type);

    /**
     * 判断文件是否存在
     *
     * @param type
     * @param fileName
     * @return
     */
    public boolean fileExistsInConfigPath(String type, String fileName);

}
