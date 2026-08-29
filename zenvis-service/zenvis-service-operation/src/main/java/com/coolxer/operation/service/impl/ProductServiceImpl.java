package com.coolxer.operation.service.impl;

import com.coolxer.operation.configuration.TopicDefine;
import com.coolxer.operation.model.*;
import com.coolxer.operation.service.ProductService;
import com.coolxer.operation.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.utility.RandomString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.Random;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @Override
    public void sendAndroidStart(AndroidStart androidStart) {
        OperationStart operationStart = new OperationStart();
        operationStart.setId(String.valueOf(androidStart.getFact().getCommon().getStartId()));
        operationStart.setOperationType("operation_start_event");
        operationStart.setUserId(androidStart.getFact().getCommon().getUserId());
        operationStart.setDeviceId(androidStart.getFact().getCommon().getGuid());
        operationStart.setDeviceOs(androidStart.getFact().getCommon().getPlatform());
        operationStart.setDeviceModel(androidStart.getFact().getCommon().getModel());
        operationStart.setAppId(String.valueOf(androidStart.getFact().getCommon().getAppId()));
        operationStart.setAppName(androidStart.getFact().getCommon().getAppName());
        operationStart.setPackageName(androidStart.getFact().getCommon().getAppPackage());
        operationStart.setLongitude(androidStart.getFact().getCommon().getLongitude());
        operationStart.setLatitude(androidStart.getFact().getCommon().getLatitude());
        operationStart.setCountry(androidStart.getFact().getCommon().getCountry());
        operationStart.setProvince(androidStart.getFact().getCommon().getProvince());
        operationStart.setCity(androidStart.getFact().getCommon().getCity());
        operationStart.setCounty(androidStart.getFact().getCommon().getThoroughfare());
        operationStart.setNetType(androidStart.getFact().getCommon().getNetType());
        operationStart.setLanIp(androidStart.getFact().getCommon().getLanIp());
        operationStart.setWanIp(androidStart.getFact().getCommon().getWanIp());
        operationStart.setEventTime(androidStart.getFact().getCommon().getClientTime());

        sendOperation(operationStart);
    }


    @Override
    public void sendAndroidActivity(AndroidActivity AndroidActivity) {
        OperationPage operationPage = new OperationPage();
        operationPage.setId(String.valueOf(AndroidActivity.getFact().getCommon().getStartId())+ new RandomString().nextString());
        operationPage.setOperationType("operation_page_event");
        operationPage.setUserId(AndroidActivity.getFact().getCommon().getUserId());
        operationPage.setStartId(String.valueOf(AndroidActivity.getFact().getCommon().getStartId()));
        operationPage.setPagePath(AndroidActivity.getFact().getBase().getClassName());
        operationPage.setPageName(AndroidActivity.getFact().getBase().getTitle());
        operationPage.setReferrer(AndroidActivity.getFact().getBase().getIntent());
        operationPage.setLongitude(AndroidActivity.getFact().getCommon().getLongitude());
        operationPage.setLatitude(AndroidActivity.getFact().getCommon().getLatitude());
        operationPage.setCountry(AndroidActivity.getFact().getCommon().getCountry());
        operationPage.setProvince(AndroidActivity.getFact().getCommon().getProvince());
        operationPage.setCity(AndroidActivity.getFact().getCommon().getCity());
        operationPage.setCounty(AndroidActivity.getFact().getCommon().getCounty());
        operationPage.setNetType(AndroidActivity.getFact().getCommon().getNetType());
        operationPage.setLanIp(AndroidActivity.getFact().getCommon().getLanIp());
        operationPage.setWanIp(AndroidActivity.getFact().getCommon().getWanIp());
        operationPage.setEventTime(AndroidActivity.getFact().getCommon().getClientTime());

        sendOperation(operationPage);
    }

    private void sendOperation(Operation operation){
        String topic = TopicDefine.TOPIC_OPERATION_ALL;
        try {
            String key = operation.getPatternKey() == null ? "null" : operation.getPatternKey();
            kafkaTemplate.send(topic, key, JacksonUtil.toJson(operation));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
