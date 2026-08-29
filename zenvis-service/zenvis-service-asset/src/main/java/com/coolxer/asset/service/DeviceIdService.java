package com.coolxer.asset.service;

import com.coolxer.asset.model.AppModel;
import com.coolxer.asset.model.DeviceModel;
import com.fasterxml.jackson.databind.JsonNode;

public interface DeviceIdService {

    /**
     * 预先生成uuid格式的deviceId，放到id队列备用
     */
    void asyncBuildDeviceId();

    /**
     * 更新设备指纹
     * @param deviceModel
     * @return
     */
    String updateDevice(DeviceModel deviceModel);

    /**
     * 更新设备的应用列表信息
     *
     * @param appModel
     */
    void updateDeviceApp(AppModel appModel);

    /**
     *  获取设备指纹
     *
     * @param guid
     * @return
     */
    String getDeviceId(String guid);

    /**
     * 根据设备指纹获取guid数组
     *
     * @param id
     * @return
     */
    String[] getGuidArray(String id);

    /**
     * 查询设备信息
     *
     * @param guid
     * @return json字符串
     */
    JsonNode getDeviceInfo(String guid);

    /**
     * 查询设备应用信息
     *
     * @param guid
     * @return json字符串
     */
    JsonNode getDeviceApp(String guid);

}
