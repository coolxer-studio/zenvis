package com.coolxer.commons.constants;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * 配置常量
 */
public class ConfigConstants {

    private ConfigConstants() {
    }

    public static final String USER_DIR_KEY = "user.dir";
    public static final String USER_DIR_PATH = System.getProperty(ConfigConstants.USER_DIR_KEY);
    public static final String CONFIG_PATH = USER_DIR_PATH + File.separator + "config";
    public static final String RESOURCES_PATH = USER_DIR_PATH + File.separator + "src" + File.separator + "main" + File.separator + "resources";
    public static final String CONFIG_RESOURCES_PATH = RESOURCES_PATH + File.separator + "config";

    /**
     * 可修改文件大小上限1MB
     */
    public static final Long MODIFIABLE_LIMIT_SIZE = 1024 * 1024L;

    /**
     * 可修改文件类型
     */
    public static final List<String> MODIFIABLE_FILE_SUFFIX_NAME = List.of("json", "properties", "txt", "csv", "xml", "conf");

    /**
     * 空文件(文件夹)
     */
    public static final Long EMPTY_FILE_SIZE = 0L;

    /**
     * 需要忽略的文件或文件夹
     */
    public static final List<String> IGNORE_FILE_OR_DIR =
            Collections.emptyList();

    /**
     * 上一次提交操作符
     */
    public static final String OP_PREV_COMMIT = "^";

    /**
     * 手动修改文件提交信息
     */
    public static final String MODIFY_BY_MANUAL_COMMIT_MSG = "[%s] file [%s] by manual";

    /**
     * 初始化git仓库时的commit message
     */
    public static final String INIT_GIT_REPOSITORY_MESSAGE = "初始化git仓库";

    /**
     * 默认的时间格式化
     */
    public static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认时间格式化
     */
    public static final SimpleDateFormat DEFAULT_DATETIME_FORMAT = new SimpleDateFormat(DEFAULT_DATETIME_PATTERN);

}
