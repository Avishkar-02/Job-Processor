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

/**
 * JobKafkaConsumer — picks up job IDs from the "job-requests" topic
 * and executes the job (simulated 25-second workload with progress updates).
 *
 * CONCURRENCY:
 * concurrency="3" means Spring Kafka creates 3 consumer threads, each with
 * its own Kafka consumer instance within the same consumer group.
 * With 3 threads, up to 3 jobs run in parallel (one per thread).
 * The topic needs at least 3 partitions for this to be effective
 * (Kafka assigns one partition per consumer thread within a group).
 *
 * ────────────────────────────────────────────────────────────────
 * BUG FIXES IN THIS FILE
 * ────────────────────────────────────────────────────────────────
 *
 * BUG FIX 1: Race condition in CANCELLED state — the consumer doesn't
 * write CANCELLED to the DB, so cancelJob() and processJob() overwrite each other.
 *
 * Original flow:
 *   cancelJob() → job.status = CANCELLED → save to DB
 *   processJob() detects isCanceled() → skips updating DB
 *   BUT: if cancelJob() fires AFTER the consumer already passed the check,
 *   the consumer finishes normally and overwrites CANCELLED with COMPLETED.
 *
 * Fix: In the cancel branch, explicitly set and persist CANCELLED status.
 * Also check the current DB status again before writing COMPLETED to avoid
 * overwriting a concurrent cancel.
 *
 * BUG FIX 2: Progress update doesn't reset Redis TTL.
 * Original saveProgress() doesn't call template.expire() — TTL only resets
 * on saveStatus() calls. A long-running job (>2 min) would have its Redis
 * key expire mid-execution. The GET /jobs/{id} would then fall back to MySQL,
 * which shows stale progress (not updated every loop).
 *
 * Fix: saveProgress() now also resets the TTL (see RedisJobStateService fix).
 *
 * BUG FIX 3: OptimisticLockException not handled.
 * JobEntity has @Version (optimistic locking). If two consumers somehow
 * pick up the same job (e.g. Kafka redelivery before idempotency check),
 * the second save throws OptimisticLockException. The original code's
 * catch(Exception e) catches it but sets FAILED — which is wrong.
 * Fix: detect this case and log it as an idempotency guard hit, not a failure.
 */
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
        this.redisService  = redisService;
        this.jobService    = jobService;
    }

    /**
     * @KafkaListener binds this method to the "job-requests" topic.
     * groupId matches the consumer group — all 3 concurrent threads share
     * the same group so each message is delivered to EXACTLY ONE thread.
     * (Different groups would each get a copy — pub/sub style.)
     */
    @KafkaListener(
            topics      = "job-requests",
            groupId     = "job-processor-group",
            concurrency = "3"   // 3 threads → 3 parallel jobs max
    )
    public void consume(Long jobId) {
        log.info("Kafka consumer picked job id={} on thread={}",
                jobId, Thread.currentThread().getName());
        processJob(jobId);
    }

    private void processJob(Long jobId) {
        try {
            JobEntity job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

            // ── Idempotency guard (first line of defence) ───────────────
            // This handles Kafka at-least-once delivery: if Kafka redelivers
            // a message (consumer crash before offset commit), we check here
            // and skip processing if the job already moved past PENDING.
            // The @Version field on JobEntity (optimistic lock) is the second
            // line of defence — it catches the race condition between the
            // check here and the save below.
            if (job.getStatus() != JobStatus.PENDING) {
                log.warn("Idempotency guard: job {} already in status {}, skipping",
                        jobId, job.getStatus());
                return;
            }

            // Mark as RUNNING — both in DB (durable) and Redis (fast reads)
            job.setStatus(JobStatus.RUNNING);
            jobRepository.save(job);
            redisService.saveStatus(jobId, JobStatus.RUNNING);
            redisService.saveProgress(jobId, 0);

            boolean cancelled = false;

            // Simulate 5-step job: each step takes 5 s, updates progress
            for (int i = 1; i <= 5; i++) {
                Thread.sleep(5000);  // Simulated work

                // Check cancel flag AFTER each sleep — this is the soonest
                // we can react to a cancel. Maximum cancel latency = 5 s.
                // ConcurrentHashMap read is thread-safe without a lock.
                if (jobService.isCanceled(jobId)) {
                    log.warn("Job {} cancelled at step {}/5", jobId, i);
                    cancelled = true;
                    break;
                }

                int progress = i * 20;  // 20%, 40%, 60%, 80%, 100%
                redisService.saveProgress(jobId, progress);
                // BUG FIX 2: saveProgress now resets TTL — see RedisJobStateService
                log.debug("Job {} progress {}%", jobId, progress);
            }

            if (cancelled) {
                // BUG FIX 1: Persist CANCELLED to DB explicitly.
                // Original code skipped this — job would remain RUNNING in DB
                // if the consumer hadn't seen the cancelJob() DB write yet.
                // Re-fetch to get the latest version (cancelJob may have updated it)
                jobRepository.findById(jobId).ifPresent(entity -> {
                    // Only write CANCELLED if it hasn't been written already
                    // (cancelJob() may have already set it)
                    if (entity.getStatus() != JobStatus.CANCELLED) {
                        entity.setStatus(JobStatus.CANCELLED);
                        jobRepository.save(entity);
                        log.info("Job {} status persisted as CANCELLED by consumer", jobId);
                    }
                });
            } else {
                // BUG FIX 1: Check for concurrent cancel before writing COMPLETED.
                // Without this check, a cancel that arrived in the last loop
                // iteration could be overwritten by COMPLETED.
                JobEntity refreshed = jobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalStateException("Job disappeared: " + jobId));

                if (refreshed.getStatus() == JobStatus.CANCELLED) {
                    log.warn("Job {} was cancelled just before completion — respecting CANCELLED", jobId);
                } else {
                    refreshed.setStatus(JobStatus.COMPLETED);
                    refreshed.setResult("Job Completed Successfully");
                    refreshed.setProgress(100);
                    jobRepository.save(refreshed);
                    log.info("Job {} completed successfully", jobId);
                }
            }

        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // BUG FIX 3: Optimistic lock conflict = duplicate delivery.
            // Another consumer thread already processed this job.
            // This is NOT a failure — it's the idempotency guard working.
            log.warn("Optimistic lock conflict for job {} — likely duplicate Kafka delivery, skipping", jobId);
            // Don't set FAILED, don't rethrow — just clean up and exit

        } catch (Exception e) {
            log.error("Job {} failed with exception", jobId, e);
            jobRepository.findById(jobId).ifPresent(entity -> {
                // Don't overwrite CANCELLED with FAILED
                if (entity.getStatus() != JobStatus.CANCELLED) {
                    entity.setStatus(JobStatus.FAILED);
                    entity.setResult("Job Failed");
                    entity.setErrorMessage(e.getMessage());
                    jobRepository.save(entity);
                }
            });
        } finally {
            // Always clean up, regardless of outcome
            jobService.clearCancelJob(jobId);
            redisService.deleteJobStore(jobId);
            // deleteJobStore: removes Redis key for terminal state.
            // Next GET /jobs/{id} will hit MySQL (source of truth).
            // This is intentional — Redis only serves hot-path (in-progress) state.
        }
    }
}