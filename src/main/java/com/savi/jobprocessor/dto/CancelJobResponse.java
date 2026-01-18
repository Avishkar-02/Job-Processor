package com.savi.jobprocessor.dto;


import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public class CancelJobResponse {

    private final Long id;
    private final JobStatus status;

    public CancelJobResponse(JobEntity job){
        this.id=job.getId();
        this.status=job.getStatus();
    }

    public Long getJobId(){return id;}
    public JobStatus getJobStatus(){return status;}
}
