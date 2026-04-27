package com.savi.jobprocessor.dto;


import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public record CancelJobResponse(
        Long id,
        JobStatus status
){

    public static CancelJobResponse from(JobEntity job){
        return new CancelJobResponse(job.getId(),job.getStatus());
    }
}
