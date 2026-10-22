package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.LabApiException;
import com.coolxer.plugin.lab.api.ResponseWrap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.coolxer.plugin.lab")
public class LabApiAdvice {

    @ExceptionHandler(LabApiException.class)
    public ResponseEntity<ResponseWrap<Map<String, Object>>> handleLabException(LabApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ResponseWrap.fail(exception.getStatus().value(), exception.getMessage()));
    }
}
