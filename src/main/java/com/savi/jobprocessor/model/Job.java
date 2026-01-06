package com.savi.jobprocessor.model;

public class Job {

    private Long id;
    private JobStatus status;
    private int progress;
    private String result;
    private String errorMessage;

    public Job(Long id){
        this.id=id;
        this.status=JobStatus.PENDING;
        this.progress=0;
    }

    public Long getId(){
        return id;
    }

    public JobStatus getStatus(){
        return status;
    }

    public void setJobStatus(JobStatus status){
        this.status=status;
    }

    public void setProgress(int progress){
        this.progress=progress;
    }

    public int getProgress(){
        return progress;
    }

    public void setResult(String result){
        this.result=result;
    }

    public String getResult(){
        return result;
    }

    public String getErrorMessage(){
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage){
        this.errorMessage=errorMessage;
    }
}
