package com.savi.jobprocessor.worker;

import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.repository.JobRepository;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Component
@Scope("prototype")
public class JobWorker implements Runnable {

    private final BlockingQueue<Long> jobQueue;
    private final JobRepository jobRepository;

    public JobWorker(BlockingQueue<Long> jobQueue,JobRepository jobRepository){
        this.jobQueue=jobQueue;
        this.jobRepository=jobRepository;
    }

    @Override
    public void run() {
        while(true){

            Long jobId=null;

            try {
                 jobId=jobQueue.take();

                 JobEntity job=jobRepository.findById(jobId)
                        .orElseThrow(()-> new IllegalStateException("Job Not Found"));

                 job.setStatus(JobStatus.RUNNING);

                for(int i=0;i<=5;i++){
                    Thread.sleep(5000);
                    job.setProgress(i*20);
                    jobRepository.save(job);
                }

                jobRepository.findById(job.getId()).ifPresent(entity ->{
                    entity.setStatus(JobStatus.COMPLETED);
                    entity.setResult("Job Completed Successfully");
                    entity.setProgress(100);
                    jobRepository.save(entity);
                });

            } catch (Exception e) {

                if(jobId!=null) {
                    jobRepository.findById(jobId).ifPresent(entity -> {
                        entity.setResult("Job Failed");
                        entity.setStatus(JobStatus.FAILED);
                        entity.setErrorMessage(e.getMessage());
                        jobRepository.save(entity);
                    });
                }
            }
        }
    }
}
