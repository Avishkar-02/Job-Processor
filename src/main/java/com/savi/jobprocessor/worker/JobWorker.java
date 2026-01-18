package com.savi.jobprocessor.worker;

import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Component
@Scope("prototype")
public class JobWorker implements Runnable {

    private final BlockingQueue<Long> jobQueue;
    private final JobRepository jobRepository;
    private final RedisJobStateService redisService;
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);


    public JobWorker(BlockingQueue<Long> jobQueue,JobRepository jobRepository,RedisJobStateService redisService){
        this.jobQueue=jobQueue;
        this.jobRepository=jobRepository;
        this.redisService=redisService;
    }

    @Override
    public void run() {
        while(true){

            log.info("Worker {} started", Thread.currentThread().getName());

            Long jobId=null;

            try {
                 jobId=jobQueue.take();
                 log.info("Worker {} picked job {}", Thread.currentThread().getName(), jobId);

                 JobEntity job=jobRepository.findById(jobId)
                        .orElseThrow(()-> new IllegalStateException("Job Not Found"));

                 job.setStatus(JobStatus.RUNNING);
                 jobRepository.save(job);

                 redisService.saveStatus(jobId,JobStatus.RUNNING);
                 redisService.saveProgress(jobId,0);

                 boolean cancelled=false;

                for(int i=0;i<=5;i++){
                    Thread.sleep(5000);

                    JobStatus currentStatus=jobRepository.findById(jobId).
                            map(JobEntity::getStatus)
                            .orElseThrow(()-> new IllegalStateException("Job not found"));

                    if (currentStatus == JobStatus.CANCELLED) {
                        log.warn("Job {} cancelled during execution", jobId);
                        cancelled = true;
                        break;
                    }

                    int progress=i*20;
                   redisService.saveProgress(jobId,i*20);
                    log.debug("Job {} progress {}%", jobId, progress);
                }

                if (!cancelled) {
                    job.setStatus(JobStatus.COMPLETED);
                    job.setResult("Job Completed Successfully");
                    job.setProgress(100);
                    jobRepository.save(job);

                    log.info("Job {} completed successfully", jobId);
                }

                redisService.deleteJobStore(jobId);


            } catch (Exception e) {

                if(jobId!=null) {
                    jobRepository.findById(jobId).ifPresent(entity -> {
                        if (entity.getStatus() != JobStatus.CANCELLED) {
                            entity.setStatus(JobStatus.FAILED);
                            entity.setResult("Job Failed");
                            entity.setErrorMessage(e.getMessage());
                            jobRepository.save(entity);
                        }
                    });
                }
                log.error("Job {} failed", jobId, e);
                redisService.deleteJobStore(jobId);
            }
        }
    }
}
