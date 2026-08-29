package com.coolxer.asset.commons.constants;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author yaoqi.li
 */
public class ConstantUtil {
  /**
   * 设备信息在redis中存储的key的格式:device-info:[guid]
   */
  public static final String DEVICE_INFO_REDIS_KEY_FORMAT = "device-info:%s";

  /**
   * 设备应用信息在redis中存储的key的格式:device-app:[guid]
   */
  public static final String DEVICE_APP_REDIS_KEY_FORMAT = "device-app:%s";

  /**
   * 设备指纹因子在redis中存储的key的格式:field-[platform]-[属性名]:[属性值]
   */
  public static final String FIELD_REDIS_KEY_FORMAT = "field-%s-%s:%s";

  /**
   * 设备指纹在redis中存储的key的格式:device-id:[guid]
   */
  public static final String DEVICE_ID_REDIS_KEY_FORMAT = "device-id:%s";

  /**
   * 设备guid在redis中存储的key的格式:device-guid:[deviceId]
   */
  public static final String DEVICE_GUID_REDIS_KEY_FORMAT = "device-guid:%s";



  public static LinkedBlockingQueue<String> DEVICE_ID_QUEUE = new LinkedBlockingQueue(10000);
}
