package com.coolxer.asset.service.impl;

import com.coolxer.asset.commons.constants.ConstantUtil;
import com.coolxer.asset.model.AppModel;
import com.coolxer.asset.model.DeviceModel;
import com.coolxer.asset.service.DeviceIdService;
import com.coolxer.asset.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;


/**
 * @author yaoqi.li
 */
@Slf4j
@Service
public class DeviceIdServiceImpl implements DeviceIdService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void asyncBuildDeviceId() {
        // TODO 是不是能放到一个component中
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (; ; ) {
                        ConstantUtil.DEVICE_ID_QUEUE.put(UUID.randomUUID().toString());
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    log.error("error", e);
                }
            }
        }, "create-id").start();
    }

    @Override
    public String updateDevice(DeviceModel deviceModel) {
        // 更新设备信息
        String deviceInfoRedisKey = String.format(ConstantUtil.DEVICE_INFO_REDIS_KEY_FORMAT,deviceModel.getGuid());
        stringRedisTemplate.opsForValue().set(deviceInfoRedisKey, deviceModel.getDeviceInfo());
        // 判断是否存在该设备信息
        long count = stringRedisTemplate.opsForZSet().unionAndStore(null,deviceModel.getUnionKeys(),"destKey");
        if(count>0){
            Set<String> deviceIds = stringRedisTemplate.opsForZSet().rangeByScore("destKey",3,4);
            if(deviceIds.size() >0){
                // TODO 存在的话是不是也需要更新一些其他信息
                // 存在直接返回
                return deviceIds.stream().findFirst().get();
            }
        }
        // 不存在的时候认为是新设备，更新设备key信息，返回新的id
        String newDeviceId = null;
        try {
            newDeviceId = ConstantUtil.DEVICE_ID_QUEUE.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (String key: deviceModel.getUnionKeys()) {
            stringRedisTemplate.opsForZSet().add(key,newDeviceId,1);
        }
        // 更新指纹关系
        stringRedisTemplate.opsForValue().set(String.format(ConstantUtil.DEVICE_ID_REDIS_KEY_FORMAT,deviceModel.getGuid()),newDeviceId);
        stringRedisTemplate.opsForSet().add(String.format(ConstantUtil.DEVICE_GUID_REDIS_KEY_FORMAT,newDeviceId),deviceModel.getGuid());
        return newDeviceId;
    }

    @Override
    public void updateDeviceApp(AppModel appModel) {
        // 更新设备信息
        String deviceAppRedisKey = String.format(ConstantUtil.DEVICE_APP_REDIS_KEY_FORMAT,appModel.getGuid());
        stringRedisTemplate.opsForValue().set(deviceAppRedisKey, appModel.getAppInfo());
    }

    @Override
    public String getDeviceId(String guid) {
        return (String) stringRedisTemplate.opsForValue().get(String.format(ConstantUtil.DEVICE_ID_REDIS_KEY_FORMAT,guid));
    }

    @Override
    public String[] getGuidArray(String id) {
        Set<String> guidSet = stringRedisTemplate.opsForSet().members(String.format(ConstantUtil.DEVICE_GUID_REDIS_KEY_FORMAT,id));
        if(Objects.nonNull(guidSet)){
            return guidSet.toArray(new String[0]);
        }
        return null;
    }

    @Override
    public JsonNode getDeviceInfo(String guid) {
        String deviceInfoRedisString = stringRedisTemplate.opsForValue().get(String.format(ConstantUtil.DEVICE_INFO_REDIS_KEY_FORMAT,guid));
        JsonNode deviceInfoJsonNode = null;
        try {
            deviceInfoJsonNode = JacksonUtil.toObject(deviceInfoRedisString, JsonNode.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return deviceInfoJsonNode;
    }

    @Override
    public JsonNode getDeviceApp(String guid) {
        String deviceAppRedisString = stringRedisTemplate.opsForValue().get(String.format(ConstantUtil.DEVICE_APP_REDIS_KEY_FORMAT,guid));
        JsonNode deviceAppJsonNode = null;
        try {
            deviceAppJsonNode = JacksonUtil.toObject(deviceAppRedisString, JsonNode.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return deviceAppJsonNode;
    }
}
