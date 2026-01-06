package com.savi.jobprocessor.service;

import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.storage.JobStore;
import com.savi.jobprocessor.worker.JobWorker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class JobService {

    private final JobStore jobStore=new JobStore();

    private final BlockingQueue<Job> jobQueue=new LinkedBlockingQueue<>();

    private final AtomicLong idGenerator=new AtomicLong(1);

    public JobService(){
        startWorkers();
    }

    public Job createJob(){

        Long id=idGenerator.getAndIncrement();

        Job job=new Job(id);

        jobStore.save(job);

        jobQueue.offer(job);

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
             Thread workerThreads=new Thread(new JobWorker(jobQueue,jobStore));

             workerThreads.setName("Worker-Thread-"+i);
             workerThreads.start();
         }
    }


}
