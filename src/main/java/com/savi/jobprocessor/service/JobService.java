package com.savi.jobprocessor.service;

import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.core.JobStatus;
import com.savi.jobprocessor.repository.JobRepository;
import com.savi.jobprocessor.worker.JobWorker;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobService {

    private final BlockingQueue<Long> jobQueue=new LinkedBlockingQueue<>();

    private final ApplicationContext context;

    private final Executor taskExecutor;

    private final JobRepository jobRepository;


    public JobService(JobRepository jobRepository, ApplicationContext context, Executor taskExecutor) {
        this.jobRepository = jobRepository;
        this.context=context;
        this.taskExecutor=taskExecutor;
        startWorkers();
    }

    public JobEntity createJob(){

        JobEntity jobEntity =new JobEntity();
        jobEntity.setStatus(JobStatus.PENDING);
        jobEntity.setProgress(0);

        JobEntity savedJob=jobRepository.save(jobEntity);
        jobQueue.add(savedJob.getId());

        return savedJob;
    }

    public JobEntity getJob(Long id){
        return jobRepository.findById(id)
                .orElse(null);
    }

    public BlockingQueue<Long> getJobQueue(){
        return jobQueue;
    }

    public void startWorkers(){
         for(int i=0;i<3;i++){

             JobWorker worker=context.getBean(JobWorker.class,jobQueue,jobRepository);
             taskExecutor.execute(worker);
         }
    }
}
