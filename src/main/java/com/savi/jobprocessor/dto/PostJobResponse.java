package com.savi.jobprocessor.dto;

import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public record PostJobResponse(
     Long id,
     JobStatus status,
     long progress
    ){

    public static PostJobResponse from(JobEntity job){
        return new PostJobResponse(
                job.getId(),
                job.getStatus(),
                job.getProgress()
        );
    }
}
