package com.savi.jobprocessor.dto;


import com.savi.jobprocessor.core.JobStatus;

public record RedisJobResponse(
     Long jobId,
     JobStatus status,
     long progress
){
}
