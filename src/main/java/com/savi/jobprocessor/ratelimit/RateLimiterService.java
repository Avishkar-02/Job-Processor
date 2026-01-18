package com.savi.jobprocessor.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiterService {

    private static final int MAX_REQUEST=5;
    private static final Duration WINDOW=Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate){
        this.redisTemplate=redisTemplate;
    }

    public void validateCreateJobRequest(String clientIp){

        String key="rate:job:create:"+clientIp;

        Long count=redisTemplate.opsForValue().increment(key);

        if(count!=null && count==1){
            redisTemplate.expire(key,WINDOW);
        }

        if(count!=null && count>MAX_REQUEST){
            throw new RateLimitExceededException(
                    "Too many job creation requests, please try again later."
            );
        }
    }
}
