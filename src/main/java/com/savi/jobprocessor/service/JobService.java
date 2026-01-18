package com.savi.jobprocessor.service;

import com.savi.jobprocessor.dto.RedisJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import com.savi.jobprocessor.worker.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.*;


@Service
public class JobService {

    private final BlockingQueue<Long> jobQueue=new LinkedBlockingQueue<>();

    private final ApplicationContext context;

    private final Executor taskExecutor;

    private final JobRepository jobRepository;

    private final RedisJobStateService redisService;

    private static final Logger log =
            LoggerFactory.getLogger(JobService.class);


    public JobService(JobRepository jobRepository, ApplicationContext context, Executor taskExecutor, RedisJobStateService redisService) {
        this.jobRepository = jobRepository;
        this.context=context;
        this.taskExecutor=taskExecutor;
        this.redisService=redisService;
        startWorkers();
    }

    public JobEntity createJob(){

        JobEntity jobEntity =new JobEntity();
        jobEntity.setStatus(JobStatus.PENDING);
        jobEntity.setProgress(0);

        JobEntity savedJob=jobRepository.save(jobEntity);
        jobQueue.add(savedJob.getId());

        log.info("Job id={} saved to DB and queued for processing", savedJob.getId());

        return savedJob;
    }

    public Object getJob(Long id){

        log.debug("Fetching job state for id={}", id);
        Optional<JobStatus> jobStatus=redisService.getJobStatus(id);
        Optional<Long> jobProgress=redisService.getProgress(id);

        if(jobProgress.isPresent() && jobStatus.isPresent()){
            log.info("Redis HIT for job id={}", id);
            return new RedisJobResponse(id,jobStatus.get(),jobProgress.get());
        }

        log.warn("Redis MISS for job id={}, falling back to DB", id);
        return jobRepository.findById(id).orElse(null);
    }

    public JobEntity cancelJob(Long id){

        log.info("Attempting to cancel job id={}", id);
        JobEntity job=jobRepository.findById(id).
                orElseThrow(()->{
                    log.warn("Cancel failed, job id={} not found", id);
                    return new IllegalStateException("Job not found");
                });

        if(job.getStatus()==JobStatus.COMPLETED ||
            job.getStatus()==JobStatus.FAILED ||
            job.getStatus()==JobStatus.CANCELLED){
            log.warn("Job id={} cannot be cancelled, current status={}", id, job.getStatus());
            throw new IllegalStateException("Job cannot be cancelled in this state:  "+job.getStatus());
        }

        job.setStatus(JobStatus.CANCELLED);
        log.info("Job id={} cancelled successfully", id);
        return jobRepository.save(job);
    }

    public BlockingQueue<Long> getJobQueue(){
        return jobQueue;
    }

    public void startWorkers(){
         for(int i=0;i<3;i++){

             JobWorker worker=context.getBean(JobWorker.class,jobQueue,jobRepository,redisService);
             taskExecutor.execute(worker);
         }
    }
}
