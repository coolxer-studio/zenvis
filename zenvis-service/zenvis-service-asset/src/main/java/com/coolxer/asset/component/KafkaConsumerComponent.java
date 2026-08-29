package com.coolxer.asset.component;

import com.coolxer.asset.commons.enums.MsgTypeEnum;
import com.coolxer.asset.commons.enums.PlatformEnum;
import com.coolxer.asset.configuration.ApplicationConfig;
import com.coolxer.asset.service.DeviceIdService;
import com.coolxer.asset.service.ProductService;
import com.coolxer.asset.utils.JacksonUtil;
import com.coolxer.asset.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * kafka 消费者
 *
 * @author yaoqi.li
 */
@Slf4j
@Component
public class KafkaConsumerComponent {

  @Autowired
  private DeviceIdService deviceIdService;

  @Autowired
  private ProductService productService;

  /**
   * 入口函数，接收kafka中的数据
   *
   * @param records
   */
  @KafkaListener(topics = {"#{'${spring.kafka.consumer.listen-topics}'.split(',')}"})
  public void listen(List<ConsumerRecord<String, String>> records) {
    // 推给线程池处理
    batchProcessingKafkaData(records);
  }


  /**
   * 处理kafka中拿到的消息
   *
   * @param records Kafka消息
   */
  @Async
  public void batchProcessingKafkaData(List<ConsumerRecord<String, String>> records) {
    // kafka的消息集合转化为DeviceModel集合
    records.stream()
        .filter(record -> Objects.nonNull(record) && Objects.nonNull(record.value()))
        .forEach(record -> {
          try {
            String topicPrefix = StringUtils.substringBefore(record.topic(),"_");
            PlatformEnum platform = PlatformEnum.valueOf(StringUtils.toRootUpperCase(topicPrefix));
            String topicSuffix = StringUtils.substringAfterLast(record.topic(),"_");
            MsgTypeEnum msgType = MsgTypeEnum.valueOf(StringUtils.toRootUpperCase(topicSuffix));
            switch (msgType){
              case START:
                processStart(platform,record.value());
                break;
              case DEVICE:
                processDevice(platform,record.value());
                break;
              case APP:
                processApp(platform,record.value());
                break;
              default:
                log.warn("unknown platform:{}", platform.getType());
                break;
            }
          } catch (Exception e) {
            log.error("record to deviceModel error:{}", record, e);
          }
        });

  }

  private void processStart(PlatformEnum platform, String startMsg) throws JsonProcessingException {
    switch (platform){
      case ANDROID:
        AndroidStart androidStart = JacksonUtil.toObject(startMsg, AndroidStart.class);
        productService.sendAndroidProbe(androidStart);
        break;
      default:
        log.warn("unknown platform:{}", platform.getType());
        break;
    }
  }
  private void processDevice(PlatformEnum platform, String deviceMsg) throws JsonProcessingException {
    Asset asset = null;
    DeviceModel deviceModel = null;
    switch (platform){
      case ANDROID:
        AndroidDevice androidDevice = JacksonUtil.toObject(deviceMsg, AndroidDevice.class);
        // 资产
        productService.sendAndroidDevice(androidDevice);
        // 设备指纹
        deviceModel = androidDevice.toDeviceModel(ApplicationConfig.deviceIdRule.getAndroid());
        break;
      case IOS:
        IosDevice iosDevice = JacksonUtil.toObject(deviceMsg, IosDevice.class);
        deviceModel = iosDevice.toDeviceModel(ApplicationConfig.deviceIdRule.getIos());
        break;
      case H5:
        H5Device h5Device = JacksonUtil.toObject(deviceMsg, H5Device.class);
        deviceModel = h5Device.toDeviceModel(ApplicationConfig.deviceIdRule.getH5());
        break;
      case WECHAT:
        WechatDevice wechatDevice = JacksonUtil.toObject(deviceMsg, WechatDevice.class);
        deviceModel = wechatDevice.toDeviceModel(ApplicationConfig.deviceIdRule.getWechat());
        break;
      case HOST:
        HostDevice hostDevice = JacksonUtil.toObject(deviceMsg, HostDevice.class);
        deviceModel = hostDevice.toDeviceModel(ApplicationConfig.deviceIdRule.getHost());
        break;
      default:
        log.warn("unknown platform:{}", platform.getType());
        break;
    }
    if(Objects.nonNull(deviceModel)){
      deviceIdService.updateDevice(deviceModel);
    }
  }

  private void processApp(PlatformEnum platform, String appMsg) throws JsonProcessingException {
    AppModel appModel = null;
    switch (platform){
      case ANDROID:
        AndroidApp androidApp = JacksonUtil.toObject(appMsg, AndroidApp.class);
        // 资产
        productService.sendAndroidApp(androidApp);
        // 设备指纹
        appModel = androidApp.toAppModel();
        break;
      case IOS:
        IosApp iosApp = JacksonUtil.toObject(appMsg, IosApp.class);
        appModel = iosApp.toAppModel();
        break;
      default:
        log.warn("unknown platform:{}", platform.getType());
        break;
    }
    if(Objects.nonNull(appModel)){
      deviceIdService.updateDeviceApp(appModel);
    }
  }

}
