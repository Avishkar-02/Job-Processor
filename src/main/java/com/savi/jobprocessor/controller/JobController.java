package com.savi.jobprocessor.controller;


import com.savi.jobprocessor.dto.GetJobResponse;
import com.savi.jobprocessor.dto.PostJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
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
    public ResponseEntity<?> createJob(){
        JobEntity job=jobService.createJob();

        return ResponseEntity
                .ok(new PostJobResponse(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable Long id){

        JobEntity job= jobService.getJob(id);

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

        return ResponseEntity.ok(new GetJobResponse(job));
    }
}
