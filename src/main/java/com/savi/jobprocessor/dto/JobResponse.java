package com.savi.jobprocessor.dto;

import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.model.JobStatus;

public class JobResponse {

    private final Long jobid;
    private final JobStatus status;
    private final int progress;
    private final String errorMessage;
    private final String result;

    public JobResponse(Job job){
        this.jobid= job.getId();
        this.status=job.getStatus();
        this.progress=job.getProgress();
        this.errorMessage=job.getErrorMessage();
        this.result=job.getResult();
    }

    public Long getJobId(){return jobid;}
    public JobStatus getStatus(){return status;}
    public int getProgress(){return progress;}
    public String getErrorMessage(){return errorMessage;}
    public String getResult(){return result;}
}
