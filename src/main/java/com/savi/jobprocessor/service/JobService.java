package com.savi.jobprocessor.service;

import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.storage.JobStore;
import com.savi.jobprocessor.worker.JobWorker;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobService {


    private final BlockingQueue<Job> jobQueue=new LinkedBlockingQueue<>();

    private final AtomicLong idGenerator=new AtomicLong(1);

    private final JobStore jobStore;

    private final ApplicationContext context;

    private final Executor taskExecutor;

    public JobService(JobStore jobStore, ApplicationContext context, Executor taskExecutor) {
        this.jobStore = jobStore;
        this.context=context;
        this.taskExecutor=taskExecutor;
        startWorkers();
    }

    public Job createJob(){

        Long id=idGenerator.getAndIncrement();
        Job job=new Job(id);

        jobStore.save(job);
        jobQueue.add(job);

        return job;
    }

    public Job getJob(Long id){
        return jobStore.findById(id);
    }

    public BlockingQueue<Job> getJobQueue(){
        return jobQueue;
    }

    public void startWorkers(){
         for(int i=0;i<3;i++){

             JobWorker worker=context.getBean(JobWorker.class,jobQueue,jobStore);
             taskExecutor.execute(worker);
         }
    }
}
