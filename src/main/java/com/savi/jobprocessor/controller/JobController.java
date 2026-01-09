package com.savi.jobprocessor.controller;


import com.savi.jobprocessor.dto.JobResponse;
import com.savi.jobprocessor.model.Job;
import com.savi.jobprocessor.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService){
        this.jobService=jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(){
        Job job=jobService.createJob();

        return ResponseEntity
                .ok(new JobResponse(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable Long id){

        Job job= jobService.getJob(id);

        if(job==null){
            return ResponseEntity
                    .status(404)
                    .body(
                            new Object(){
                                public final String message="Job Not Found";
                                public final Long jobid= id;
                            }
                    );
        }

        return ResponseEntity.ok(new JobResponse(job));
    }
}
