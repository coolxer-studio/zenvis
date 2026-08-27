package com.coolxer.web;

import com.coolxer.configuration.CustomWebConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * desc
 */
@SpringBootTest
public class ReadJsonFileTest {

    private static final Logger log = LoggerFactory.getLogger(ReadJsonFileTest.class);

    @Autowired
    private CustomWebConfig customWebConfig;


    private String readJsonFile(String filePath) {

        InputStream configInputStream = getClass().getResourceAsStream(filePath);

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(configInputStream).toString();

        } catch (IOException e) {
            log.error("读取json文件失败", e);
        }
        return null;
    }
}
