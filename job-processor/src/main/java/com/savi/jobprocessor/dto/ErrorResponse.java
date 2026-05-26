package com.savi.jobprocessor.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timeStamp) {

    public static ErrorResponse of(int status,String error,String message){
        return new ErrorResponse(status,error,message, LocalDateTime.now());
    }

}
