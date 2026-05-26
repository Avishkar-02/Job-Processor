package com.savi.jobprocessor.dto;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error,
        LocalDateTime timeStamp
) {

    public static <T> ApiResponse<T> success(T data){
        return new ApiResponse<>(true,data,null,LocalDateTime.now());
    }

    public static <T> ApiResponse<T> failure(ErrorResponse error){
        return new ApiResponse<>(false,null,error,LocalDateTime.now());
    }

}
