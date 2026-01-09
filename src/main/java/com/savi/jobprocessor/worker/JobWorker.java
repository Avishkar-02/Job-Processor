package com.savi.jobprocessor.worker;

import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.model.JobStatus;
import com.savi.jobprocessor.storage.JobStore;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Component
@Scope("prototype")
public class JobWorker implements Runnable {

    private final JobStore jobStore;
    private final BlockingQueue<Job> jobQueue;

    public JobWorker(BlockingQueue<Job> jobQueue,JobStore jobStore){
        this.jobStore=jobStore;
        this.jobQueue=jobQueue;
    }

    @Override
    public void run() {
        while(true){

            Job job=null;

            try {
                 job=jobQueue.take();

                job.setJobStatus(JobStatus.RUNNING);

                for(int i=0;i<=5;i++){
                    Thread.sleep(5000);
                    job.setProgress(i*20);
                    jobStore.save(job);
                }

                job.setJobStatus(JobStatus.COMPLETED);
                job.setResult("Job Successfully Completed");
                jobStore.save(job);

            } catch (Exception e) {

                if(job!=null){
                    job.setJobStatus(JobStatus.FAILED);
                    job.setErrorMessage(e.getMessage());
                    jobStore.save(job);
                }

            }
        }
    }
}
