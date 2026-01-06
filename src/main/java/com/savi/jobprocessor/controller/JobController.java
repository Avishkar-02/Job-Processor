package com.savi.jobprocessor.controller;


import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.service.JobService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService=new JobService();

    @PostMapping
    public Job createJob(){
        return jobService.createJob();
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable Long id){
        return jobService.getJob(id);
    }
}
