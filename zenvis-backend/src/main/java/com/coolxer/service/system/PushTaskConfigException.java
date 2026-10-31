package com.coolxer.service.system;

/**
 * 推送配置在进入写入阶段前即已失败。
 * <p>该异常用于区分确定性的配置门禁失败与写入结果未知。</p>
 */
public class PushTaskConfigException extends IllegalStateException {

    public PushTaskConfigException(String message) {
        super(message);
    }

    public PushTaskConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
