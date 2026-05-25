package com.savi.jobprocessor.event;

public class JobCreatedEvent {
    private final Long jobId;

    public JobCreatedEvent(Long jobId) {
        this.jobId = jobId;
    }

    public Long getJobId() {
        return jobId;
    }
}