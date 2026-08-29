package com.coolxer.risk.service.impl;

import com.coolxer.risk.configuration.TopicDefine;
import com.coolxer.risk.model.FactMsg;
import com.coolxer.risk.model.Risk;
import com.coolxer.risk.model.RiskBaseLine;
import com.coolxer.risk.service.ProductService;
import com.coolxer.risk.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Collections;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @Override
    public void sendFactMsg(FactMsg factMsg) {
        for (FactMsg.Agenda agenda:factMsg.getAgendas()) {
            switch (agenda.getTag()) {
                case "proxy":
                    RiskBaseLine riskBaseLine = new RiskBaseLine();
                    riskBaseLine.setRiskType("proxy");
                    riskBaseLine.setRiskLevel("low");
                    riskBaseLine.setExpectedValue("0");
                    riskBaseLine.setCurrentValue("1");
                    riskBaseLine.setVerificationMethod("manual");
                    riskBaseLine.setVerificationResult("pass");
                    riskBaseLine.setLabel(Collections.singletonList("proxy"));
                    riskBaseLine.setUserId(factMsg.getFact().getCommon().getUserId());
                    riskBaseLine.setStartId(String.valueOf(factMsg.getFact().getCommon().getStartId()));
                    riskBaseLine.setAssetId(factMsg.getFact().getCommon().getGuid());
                    riskBaseLine.setNetType(factMsg.getFact().getCommon().getNetType());
                    riskBaseLine.setLanIp(factMsg.getFact().getCommon().getLanIp());
                    riskBaseLine.setWanIp(factMsg.getFact().getCommon().getWanIp());
                    riskBaseLine.setConfigurationName("proxy");
                    sendRisk(riskBaseLine);
                    break;
                default:
                    break;
            }
        }
    }

    private void sendRisk(Risk risk){
        String topic = TopicDefine.TOPIC_RISK_ALL;
        try {
            String key = risk.getPatternKey() == null ? "null" : risk.getPatternKey();
            kafkaTemplate.send(topic, key, JacksonUtil.toJson(risk));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
