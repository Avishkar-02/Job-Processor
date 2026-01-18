package com.savi.jobprocessor.ratelimit;

public class RateLimitExceededException extends RuntimeException{

    public RateLimitExceededException(String message){
        super(message);
    }
}
