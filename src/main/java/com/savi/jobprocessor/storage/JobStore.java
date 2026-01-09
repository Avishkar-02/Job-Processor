package com.savi.jobprocessor.storage;

import com.savi.jobprocessor.model.Job;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStore {

    private final Map<Long, Job>jobs=new ConcurrentHashMap<>();

    public void save(Job job){
        jobs.put(job.getId(),job);
    }

    public Job findById(Long id){
        return jobs.get(id);
    }
}
