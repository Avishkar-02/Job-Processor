package com.savi.jobprocessor.controller;


import com.savi.jobprocessor.dto.CancelJobResponse;
import com.savi.jobprocessor.dto.PostJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;
    private static final Logger log= LoggerFactory.getLogger(JobController.class);

    public JobController(JobService jobService){
        this.jobService=jobService;
    }

    @PostMapping
    public ResponseEntity<?> createJob(){
        log.info("Received Request to create job");
        JobEntity job=jobService.createJob();

        log.info("Job Created Successfully with id={}",job.getId());
        return ResponseEntity
                .ok(new PostJobResponse(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable Long id){

        log.info("Received Request to fetch job id={} ",id);
        Object job= jobService.getJob(id);

        if(job==null){
            log.warn("Job id={} not Found",id);
            return ResponseEntity
                    .status(404)
                    .body(
                            new Object(){
                                public final String message="Job Not Found";
                                public final Long jobid= id;
                            }
                    );
        }

        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id){

        log.info("Received Request to Cancel Job id={}",id);
        try{
            JobEntity cancelledJob= jobService.cancelJob(id);
            return ResponseEntity.ok(new CancelJobResponse(cancelledJob));

        }catch (IllegalStateException ex){

            if(ex.getMessage().contains("not found")){
                    return ResponseEntity.status(404).body(
                            new Object(){
                                public String message="Job Not Found";
                                public final Long jobId=id;
                            }
                    );
            }

            return ResponseEntity.status(409).body(
                    new Object(){
                        public final String message= ex.getMessage();
                        public final Long jobId=id;
                    }
            );
        }
    }
}
