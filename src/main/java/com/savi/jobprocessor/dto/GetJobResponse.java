package com.savi.jobprocessor.dto;

import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;

public class GetJobResponse {

    private final Long jobId;
    private final JobStatus status;
    private final long progress;
    private final String result;
    private final String errorMessage;

    public GetJobResponse(JobEntity job){
        this.jobId= job.getId();
        this.status=job.getStatus();
        this.progress=job.getProgress();
        this.result=job.getResult();
        this.errorMessage=job.getErrorMessage();

    }

    public Long getJobId(){return jobId;}
    public JobStatus getStatus(){return status;}
    public long getProgress(){return progress;}
    public String getResult(){return result;}
    public String getErrorMessage(){return errorMessage;}

}
