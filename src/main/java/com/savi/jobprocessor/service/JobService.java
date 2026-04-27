package com.savi.jobprocessor.service;

import com.savi.jobprocessor.dto.GetJobResponse;
import com.savi.jobprocessor.dto.RedisJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.kafka.JobKafkaProducer;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.*;


@Service
public class JobService {

    private final ConcurrentHashMap<Long,Boolean>cancelFlag= new ConcurrentHashMap<>();

    private final JobRepository jobRepository;
    private final RedisJobStateService redisService;
    private final JobKafkaProducer jobKafkaProducer;

    private static final Logger log =
            LoggerFactory.getLogger(JobService.class);


    public JobService(JobRepository jobRepository, RedisJobStateService redisService,JobKafkaProducer jobKafkaProducer) {
        this.jobRepository = jobRepository;
        this.redisService=redisService;
        this.jobKafkaProducer=jobKafkaProducer;
        //  startWorkers();
    }

    @Transactional
    public JobEntity createJob(){

        JobEntity jobEntity=JobEntity.builder()
                .status(JobStatus.PENDING)
                .progress(0)
                .build();

        JobEntity savedJob=jobRepository.save(jobEntity);
        jobKafkaProducer.publishJob(savedJob.getId());
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
        return jobRepository.findById(id).map(GetJobResponse::from).orElse(null);
    }

    @Transactional
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

        cancelFlag.put(id,true);
        job.setStatus(JobStatus.CANCELLED);
        log.info("Job id={} cancelled successfully", id);
        return jobRepository.save(job);
    }

    public boolean isCanceled(Long jobId){
        return cancelFlag.getOrDefault(jobId,false);
    }

    public void clearCancelJob(Long jobId){
        cancelFlag.remove(jobId);
    }


}
