package com.savi.jobprocessor.dto;


import com.savi.jobprocessor.core.JobStatus;

public class RedisJobResponse {

    private final Long jobId;
    private final JobStatus status;
    private final long progress;

    public RedisJobResponse(Long jobId,JobStatus status,long progress){
        this.jobId=jobId;
        this.status=status;
        this.progress=progress;
    }

    public Long getJobId(){return jobId;}
    public JobStatus getStatus(){return status;}
    public long getProgress(){return  progress;}
}
