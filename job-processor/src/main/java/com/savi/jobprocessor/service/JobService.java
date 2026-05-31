package com.savi.jobprocessor.service;

import com.savi.jobprocessor.dto.GetJobResponse;
import com.savi.jobprocessor.dto.RedisJobResponse;
import com.savi.jobprocessor.dto.PostJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.event.JobCreatedEvent;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import com.savi.jobprocessor.kafka.JobKafkaProducer;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final ConcurrentHashMap<Long, Boolean> cancelFlag = new ConcurrentHashMap<>();

    private final JobRepository jobRepository;
    private final RedisJobStateService redisService;
    private final JobKafkaProducer jobKafkaProducer;
    private final ApplicationEventPublisher eventPublisher;

    public JobService(JobRepository jobRepository,
                      RedisJobStateService redisService,
                      JobKafkaProducer jobKafkaProducer,
                      ApplicationEventPublisher eventPublisher) {
        this.jobRepository   = jobRepository;
        this.redisService    = redisService;
        this.jobKafkaProducer = jobKafkaProducer;
        this.eventPublisher  = eventPublisher;
    }

    /*
     * WHY @Transactional + TransactionalEventListener?
     *
     * THE BUG THIS FIXES:
     * Before: jobRepository.save() then kafkaProducer.publish() inside same method.
     * Kafka is fast — it delivers the message to the consumer in ~5ms.
     * But @Transactional doesn't commit until the METHOD RETURNS.
     * So the consumer calls jobRepository.findById() and gets "not found"
     * because MySQL hasn't committed yet. This caused "Job not found" errors.
     *
     * THE FIX:
     * 1. Save to DB and publish a Spring ApplicationEvent inside the transaction.
     * 2. @TransactionalEventListener(AFTER_COMMIT) fires ONLY after MySQL commits.
     * 3. Then we publish to Kafka — now the consumer will always find the job in DB.
     *
     * Spring's ApplicationEventPublisher is built-in — no extra dependency needed.
     */
    @Transactional
    public JobEntity createJob() {
        JobEntity jobEntity = JobEntity.builder()
                .status(JobStatus.PENDING)
                .progress(0)
                .build();

        JobEntity saved = jobRepository.save(jobEntity);
        log.info("Job id={} saved to DB and queued for processing", saved.getId());

        // Publish Spring event — Kafka publish will happen AFTER this transaction commits
        // NOT immediately — see onJobCreated() below
        eventPublisher.publishEvent(new JobCreatedEvent(saved.getId()));

        return saved;
    }

    /*
     * This method fires AFTER the @Transactional createJob() commits to MySQL.
     * AFTER_COMMIT guarantee: by the time this runs, any thread doing
     * jobRepository.findById(id) will find the row — no more "Job not found".
     *
     * This is NOT inside a transaction itself — it runs after commit.
     * If Kafka publish fails here, the job is saved in DB with PENDING status.
     * That's acceptable — it's a known trade-off vs full 2PC which is overkill here.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(JobCreatedEvent event) {
        log.info("Transaction committed — publishing job id={} to Kafka", event.getJobId());
        jobKafkaProducer.publishJob(event.getJobId());
    }


    public Object getJob(Long id) {
        log.debug("Fetching job state for id={}", id);

        Optional<JobStatus> jobStatus  = redisService.getJobStatus(id);
        Optional<Long>      jobProgress = redisService.getProgress(id);

        if (jobProgress.isPresent() && jobStatus.isPresent()) {
            log.info("Redis HIT for job id={}", id);
            return new RedisJobResponse(id, jobStatus.get(), jobProgress.get());
        }

        log.warn("Redis MISS for job id={}, falling back to DB", id);
        return jobRepository.findById(id).map(GetJobResponse::from).orElse(null);
    }

    @Transactional
    public JobEntity cancelJob(Long id) {
        log.info("Attempting to cancel job id={}", id);

        JobEntity job = jobRepository.findById(id).orElseThrow(() -> {
            log.warn("Cancel failed, job id={} not found", id);
            return new IllegalStateException("Job not found");
        });

        if (job.getStatus() == JobStatus.COMPLETED ||
                job.getStatus() == JobStatus.FAILED    ||
                job.getStatus() == JobStatus.CANCELLED) {
            log.warn("Job id={} cannot be cancelled, current status={}", id, job.getStatus());
            throw new IllegalStateException("Job cannot be cancelled in this state: " + job.getStatus());
        }

        // Set in-memory flag FIRST — consumer checks this every 5s sleep cycle
        cancelFlag.put(id, true);
        job.setStatus(JobStatus.CANCELLED);
        log.info("Job id={} cancelled successfully", id);
        return jobRepository.save(job);
    }

    public boolean isCanceled(Long jobId) {
        return cancelFlag.getOrDefault(jobId, false);
    }

    public void clearCancelJob(Long jobId) {
        cancelFlag.remove(jobId);
    }
}