package com.coolxer.model.config.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a configuration file or directory rename/delete operation.
 */
@Data
@Accessors(chain = true)
public class ConfigPathOperationResult {

    private boolean success;

    private String operation;

    private String kind;

    private String sourcePath;

    private String targetPath;

    private boolean changed;

    private boolean applied;

    private List<String> affectedPaths = new ArrayList<>();

    private String message;

    public static ConfigPathOperationResult failure(String operation,
                                                    String sourcePath,
                                                    String message) {
        return new ConfigPathOperationResult()
                .setSuccess(false)
                .setOperation(operation)
                .setSourcePath(sourcePath)
                .setChanged(false)
                .setApplied(false)
                .setMessage(message);
    }
}
