package com.savi.jobprocessor.redis;

import com.savi.jobprocessor.core.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;


@Service
public class RedisJobStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisJobStateService.class);

    private final StringRedisTemplate template;

    private static final Duration JOB_TTL = Duration.ofMinutes(2);

    public RedisJobStateService(StringRedisTemplate template) {
        this.template = template;
    }

    /**
     * Redis key for a job's hash. Namespace pattern: "job:{id}".
     * Prefix "job:" prevents collision with the rate limiter's "rate:job:create:{ip}" keys.
     */
    private String jobKey(Long jobId) {
        return "job:" + jobId;
    }

    /**
     * Saves the job status and resets TTL.
     * Called at RUNNING state transition (start of processing).
     */
    public void saveStatus(Long jobId, JobStatus status) {
        String key = jobKey(jobId);
        template.opsForHash().put(key, "status", status.name());
        template.expire(key, JOB_TTL);  // Reset TTL on status change
        log.debug("Redis status saved: job={} status={}", jobId, status);
    }

    /**
     * Saves the current progress percentage (0-100) and resets TTL.
     *
     * BUG FIX: Added template.expire() here.
     * Original: progress updates did NOT reset TTL, so a key set to expire
     * at T+2min wouldn't be refreshed by progress updates at T+1:50, T+1:55…
     * leading to premature key expiry for long or delayed jobs.
     */
    public void saveProgress(Long jobId, long progress) {
        String key = jobKey(jobId);
        template.opsForHash().put(key, "progress", String.valueOf(progress));
        template.expire(key, JOB_TTL);
    }

    /**
     * Reads the job status from Redis.
     * Returns Optional.empty() on cache miss (key expired or job not yet cached).
     * The caller (JobService.getJob) then falls back to MySQL.
     */
    public Optional<JobStatus> getJobStatus(Long jobId) {
        String key = jobKey(jobId);
        Object value = template.opsForHash().get(key, "status");

        if (value == null) {
            log.debug("Redis status MISS for job {}", jobId);
            return Optional.empty();
        }

        log.debug("Redis status HIT for job {} → {}", jobId, value);
        return Optional.of(JobStatus.valueOf(value.toString()));
    }


    public Optional<Long> getProgress(Long jobId) {
        String key = jobKey(jobId);
        Object value = template.opsForHash().get(key, "progress");

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(value.toString()));
    }

    /**
     * Deletes the entire job hash from Redis.
     * Called after a job reaches a terminal state (COMPLETED, FAILED, CANCELLED).
     * After this, GET /jobs/{id} will always hit MySQL — Redis key is gone.
     * This is intentional: Redis only serves the hot (in-progress) path.
     * Terminal state reads go to MySQL (source of truth), which has the
     * full result/errorMessage fields that Redis doesn't store.
     */
    public void deleteJobStore(Long jobId) {
        template.delete(jobKey(jobId));
        log.debug("Redis key deleted for job {} (terminal state reached)", jobId);
    }
}