package com.coolxer.asset.service.impl;

import com.coolxer.asset.commons.enums.asset.*;
import com.coolxer.asset.configuration.TopicDefine;
import com.coolxer.asset.model.*;
import com.coolxer.asset.model.Asset;
import com.coolxer.asset.service.ProductService;
import com.coolxer.asset.utils.DateUtil;
import com.coolxer.asset.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @Override
    public void sendAndroidProbe(AndroidStart androidStart) {
        AssetProbe assetProbe = new AssetProbe();
        assetProbe.setId(androidStart.getFact().getCommon().getGuid());
        assetProbe.setAssetType("asset_probe");
        assetProbe.setSource(AssetSource.AGENT.name());
        assetProbe.setType(AssetType.BUSINESS.name());
        assetProbe.setOwner(AssetOwner.CUSTOMER.name());
        assetProbe.setStatus(AssetStatus.ONLINE.name());
        assetProbe.setLabel(Collections.emptyList());
        assetProbe.setAccess(true);
        assetProbe.setLevel(AssetLevel.GENERAL.name());
        assetProbe.setRisk(AssetRiskLevel.NONE.name());
        assetProbe.setRiskInfo("xxx");

        assetProbe.setProbeName("Android 探针");
        assetProbe.setProbeVersion(androidStart.getFact().getCommon().getSdkVersion());
        sendAsset(assetProbe);
    }

    @Override
    public void sendAndroidDevice(AndroidDevice androidDevice) {
        AssetMobileDevice assetMobileDevice = new AssetMobileDevice();
        assetMobileDevice.setId(androidDevice.getFact().getCommon().getGuid());
        assetMobileDevice.setAssetType("asset_mobile");
        assetMobileDevice.setSource(AssetSource.AGENT.name());
        assetMobileDevice.setType(AssetType.BUSINESS.name());
        assetMobileDevice.setOwner(AssetOwner.CUSTOMER.name());
        assetMobileDevice.setStatus(AssetStatus.ONLINE.name());
        assetMobileDevice.setLabel(Collections.emptyList());
        assetMobileDevice.setAccess(true);
        assetMobileDevice.setLevel(AssetLevel.GENERAL.name());
        assetMobileDevice.setRisk(AssetRiskLevel.NONE.name());
        assetMobileDevice.setRiskInfo("xxx");
        assetMobileDevice.setAreaCode("unknown");
        assetMobileDevice.setCountry("unknown");
        assetMobileDevice.setProvince("unknown");
        assetMobileDevice.setCity("unknown");
        assetMobileDevice.setCounty("unknown");
        assetMobileDevice.setNetType(androidDevice.getFact().getNetwork().getType());
        assetMobileDevice.setLanIp(androidDevice.getFact().getCommon().getLanIp());
        assetMobileDevice.setWanIp(androidDevice.getFact().getCommon().getWanIp());
        assetMobileDevice.setBrand(androidDevice.getFact().getCommon().getManufacturer());
        assetMobileDevice.setModel(androidDevice.getFact().getCommon().getModel());
        assetMobileDevice.setManufacturer(androidDevice.getFact().getCommon().getManufacturer());
        assetMobileDevice.setModel(androidDevice.getFact().getCommon().getModel());
        assetMobileDevice.setSystemName(androidDevice.getFact().getDevice().getSystemName());
        assetMobileDevice.setSystemVersion(androidDevice.getFact().getDevice().getSystemVersion());
        assetMobileDevice.setAndroidId(androidDevice.getFact().getUuid().getAndroidId());
        assetMobileDevice.setBuildId(androidDevice.getFact().getDevice().getBuildId());
        assetMobileDevice.setBluetoothMac("");
        assetMobileDevice.setDeviceFingerprint(androidDevice.getFact().getUuid().getGuid());
        assetMobileDevice.setInfo(androidDevice.getFact());
        sendAsset(assetMobileDevice);
    }

    @Override
    public void sendAndroidApp(AndroidApp androidApp) {
        List<AssetApp> assetAppList = new ArrayList<>();
        for (AndroidApp.App app:androidApp.getFact().getInstalled()) {
            AssetApp assetApp = new AssetApp();
            assetApp.setId(app.getPackageName()+app.getCertMd5());
            assetApp.setAssetType("asset_app");
            assetApp.setSource(AssetSource.AGENT.name());
            assetApp.setType(AssetType.BUSINESS.name());
            assetApp.setOwner(AssetOwner.CUSTOMER.name());
            assetApp.setStatus(AssetStatus.ONLINE.name());
            assetApp.setLabel(Collections.emptyList());
            assetApp.setAccess(true);
            assetApp.setLevel(AssetLevel.GENERAL.name());
            assetApp.setRisk(AssetRiskLevel.NONE.name());
            assetApp.setRiskInfo("xxx");

            assetApp.setAppName(app.getAppName());
            assetApp.setAppVersion(app.getVersionName());
            assetApp.setAppType("Enterprise");
            assetApp.setPlatform("Android");
            assetApp.setPackageName(app.getPackageName());
            assetApp.setDeveloper(app.getCertIssuer());
            assetApp.setPublishTime("2025-11-01 02:18:50");
            assetApp.setUpdateChannel("Google Play");
            assetApp.setFileMd5(app.getMd5());
            assetApp.setCertificateMd5(app.getCertMd5());
            assetApp.setInfo(app);
            sendAsset(assetApp);
        }

    }

    private void sendAsset(Asset asset){
        String topic = TopicDefine.TOPIC_ASSET_ALL;
        try {
            String key = asset.getPatternKey() == null ? "null" : asset.getPatternKey();
            kafkaTemplate.send(topic, key, JacksonUtil.toJson(asset));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
