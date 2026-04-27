package com.savi.jobprocessor.exception;

import com.savi.jobprocessor.dto.ApiResponse;
import com.savi.jobprocessor.ratelimit.RateLimitExceededException;
import com.savi.jobprocessor.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log= LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>>handleRateLimit(RateLimitExceededException ex){
        log.warn("Rate Limit Exceeded: {}",ex.getMessage());
        ErrorResponse error=ErrorResponse.of(429,"Too Many Requests",ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<?>>handleIllegalState(IllegalStateException ex){
        log.warn("Illegal State: {}",ex.getMessage());

        if(ex.getMessage().contains("not found")){
            ErrorResponse error=ErrorResponse.of(404,"Not found",ex.getMessage());
            return  ResponseEntity.
                    status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(error));
        }

        ErrorResponse error=ErrorResponse.of(409,"Conflict",ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>>handleGeneric(Exception ex){
        log.error("Unexpected Error",ex);
        ErrorResponse error=ErrorResponse.of(500,"Internal Server Error", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(error));
    }
}
