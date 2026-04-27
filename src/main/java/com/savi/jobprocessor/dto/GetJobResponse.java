package com.savi.jobprocessor.dto;

import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public record GetJobResponse(
     Long jobId,
     JobStatus status,
     long progress,
     String result,
     String errorMessage

){
    public static GetJobResponse from(JobEntity job){
        return new GetJobResponse(
                job.getId(),
                job.getStatus(),
                job.getProgress(),
                job.getResult(),
                job.getErrorMessage()
        );
    }
}
