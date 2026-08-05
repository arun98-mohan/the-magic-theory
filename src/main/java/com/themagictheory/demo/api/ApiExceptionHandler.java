package com.themagictheory.demo.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Malformed JSON or an unparseable field (e.g. a bad timestamp). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformedBody(HttpMessageNotReadableException e) {
        String detail = e.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", "malformed request body: " + detail));
    }
}
