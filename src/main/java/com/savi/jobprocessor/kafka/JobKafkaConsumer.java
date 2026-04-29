package com.savi.jobprocessor.kafka;

import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import com.savi.jobprocessor.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class JobKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobKafkaConsumer.class);

    private final JobRepository jobRepository;
    private final RedisJobStateService redisService;
    private final JobService jobService;

    public JobKafkaConsumer(JobRepository jobRepository,
                            RedisJobStateService redisService,
                            JobService jobService) {
        this.jobRepository = jobRepository;
        this.redisService = redisService;
        this.jobService = jobService;
    }

    @KafkaListener(
            topics = "job-requests",
            groupId = "job-processor-group",
            concurrency = "3"
    )
    public void consume(Long jobId) {
        log.info("Kafka consumer picked job id={} on thread={}",
                jobId, Thread.currentThread().getName());
        processJob(jobId);
    }

    private void processJob(Long jobId) {
        try {

            JobEntity job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found"));

            // Idempotency guard
            if (job.getStatus() != JobStatus.PENDING) {
                log.warn("Job {} already in status {}, skipping", jobId, job.getStatus());
                return;
            }

            job.setStatus(JobStatus.RUNNING);
            jobRepository.save(job);
            redisService.saveStatus(jobId, JobStatus.RUNNING);
            redisService.saveProgress(jobId, 0);

            boolean cancelled = false;

            for (int i = 0; i <= 5; i++) {
                Thread.sleep(5000);

                if (jobService.isCanceled(jobId)) {
                    log.warn("Job {} cancelled during execution", jobId);
                    cancelled = true;
                    break;
                }

                int progress = i * 20;
                redisService.saveProgress(jobId, progress);
                log.debug("Job {} progress {}%", jobId, progress);
            }

            if (!cancelled) {
                job.setStatus(JobStatus.COMPLETED);
                job.setResult("Job Completed Successfully");
                job.setProgress(100);
                jobRepository.save(job);
                log.info("Job {} completed successfully", jobId);
            }

            jobService.clearCancelJob(jobId);
            redisService.deleteJobStore(jobId);

        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            jobRepository.findById(jobId).ifPresent(entity -> {
                if (entity.getStatus() != JobStatus.CANCELLED) {
                    entity.setStatus(JobStatus.FAILED);
                    entity.setResult("Job Failed");
                    entity.setErrorMessage(e.getMessage());
                    jobRepository.save(entity);
                }
            });
            jobService.clearCancelJob(jobId);
            redisService.deleteJobStore(jobId);
        }
    }
}