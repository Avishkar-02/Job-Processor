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

    private final StringRedisTemplate template;
    private final Duration JOB_TTL=Duration.ofMinutes(2);
    private static final Logger log = LoggerFactory.getLogger(RedisJobStateService.class);

    public RedisJobStateService(StringRedisTemplate template){
        this.template=template;
    }

    private String jobKey(Long jobid){
        return "job:"+jobid;
    }

    public void saveStatus(Long jobid, JobStatus status){

        String key=jobKey(jobid);

        template.opsForHash().put(
                key,
                "status",
                status.name()
        );

        log.debug("Redis status saved for job {} -> {}", jobid, status);
        template.expire(key,JOB_TTL);
    }

    public void saveProgress(Long jobid,long progress){

        String key=jobKey(jobid);

        template.opsForHash().put(
                key,
                "progress",
                String.valueOf(progress)
        );
    };
    public Optional<JobStatus>getJobStatus(Long jobid){

        String key=jobKey(jobid);

        Object value=template.opsForHash().get(key,"status");

        if(value==null){
            log.debug("Redis status miss for job {}", jobid);
            return Optional.empty();
        }

        return Optional.of(JobStatus.valueOf(value.toString()));
    }

    public Optional<Long>getProgress(Long jobid){

        String key=jobKey(jobid);

        Object value=template.opsForHash().get(key,"progress");

        if(value==null){
            return Optional.empty();
        }

        return Optional.of(Long.valueOf(value.toString()));
    }

    public void deleteJobStore(Long jobid){

        String key=jobKey(jobid);

        template.delete(key);
    }
}
