/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coolxer.utils;


import com.coolxer.model.dih.ai.AIModel;
import com.coolxer.model.dih.ai.AIModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ModelsUtils
 */

public final class ModelsUtils {

    private final static String MODELS_FILE_PATH = "models.yaml";

    private static final String MODEL = "model";

    private static final String DESC = "desc";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    private ModelsUtils() {
    }

    public static List<Map<String, String>> getModels() throws IOException {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        InputStream resourceAsStream = ModelsUtils.class.getClassLoader().getResourceAsStream(MODELS_FILE_PATH);
        AIModels models = mapper.readValue(resourceAsStream, AIModels.class);
        List<Map<String, String>> resultSet = new ArrayList<>();
        for (AIModel model : models.getModels()) {
            String modelName = resolvePlaceholders(model.getName());
            if (modelName == null || modelName.isBlank()) {
                continue;
            }
            Map<String, String> modelMap = new HashMap<>();
            modelMap.put(MODEL, modelName);
            modelMap.put(DESC, model.getDescription());
            resultSet.add(modelMap);
        }
        return resultSet;
    }

    private static String resolvePlaceholders(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String replacement = System.getenv(matcher.group(1));
            if (replacement == null) {
                replacement = System.getProperty(matcher.group(1));
            }
            if (replacement == null) {
                replacement = matcher.group(2) == null ? "" : matcher.group(2);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

}
