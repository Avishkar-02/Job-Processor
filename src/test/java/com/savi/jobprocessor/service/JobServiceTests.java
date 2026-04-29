package com.savi.jobprocessor.service;

import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.kafka.JobKafkaProducer;
import com.savi.jobprocessor.redis.RedisJobStateService;
import com.savi.jobprocessor.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTests {

    @Mock
    JobRepository jobRepository;

    @Mock
    JobKafkaProducer jobKafkaProducer;

    @Mock
    RedisJobStateService redisService;

    @InjectMocks
    JobService jobService;

    @Test
    void createJob_shouldSaveJobAndPublishToKafka(){

        JobEntity savedJob = new JobEntity();
        savedJob.setId(1L);

        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedJob);

        JobEntity result = jobService.createJob();

        assertNotNull(result);
        assertEquals(1L, result.getId());

        verify(jobRepository).save(any(JobEntity.class));
        verify(jobKafkaProducer).publishJob(1L);
    }

    @Test
    void createJob_shouldThrowException_whenSaveFails() {

        when(jobRepository.save(any(JobEntity.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> jobService.createJob());

        verify(jobKafkaProducer, never()).publishJob(any());
    }


    @Test
    void getJob_shouldReturnFromRedis_whenCacheHit() {

        when(redisService.getJobStatus(1L)).thenReturn(Optional.of(com.savi.jobprocessor.core.JobStatus.RUNNING));
        when(redisService.getProgress(1L)).thenReturn(Optional.of(60L));

        Object result = jobService.getJob(1L);

        assertNotNull(result);
        verifyNoInteractions(jobRepository);
    }
}