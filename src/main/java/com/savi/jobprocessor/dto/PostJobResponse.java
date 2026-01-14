package com.savi.jobprocessor.dto;

import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public class PostJobResponse {

    private final Long id;
    private final JobStatus status;
    private final long progress;

    public PostJobResponse(JobEntity job){
        this.id=job.getId();
        this.status=job.getStatus();
        this.progress=job.getProgress();
    }

    public long getId(){return id;}
    public long getProgress(){return progress;}
    public JobStatus getStatus(){return status;}

}
