package com.savi.jobprocessor.controller;


import com.savi.jobprocessor.dto.ApiResponse;
import com.savi.jobprocessor.dto.CancelJobResponse;
import com.savi.jobprocessor.dto.ErrorResponse;
import com.savi.jobprocessor.dto.PostJobResponse;
import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.ratelimit.RateLimiterService;
import com.savi.jobprocessor.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;
    private final RateLimiterService rateLimiterService;
    private static final Logger log= LoggerFactory.getLogger(JobController.class);

    public JobController(JobService jobService, RateLimiterService rateLimiterService){
        this.jobService=jobService;
        this.rateLimiterService=rateLimiterService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostJobResponse>> createJob(HttpServletRequest request){

            String clientIp=request.getRemoteAddr();
            rateLimiterService.validateCreateJobRequest(clientIp);

            log.info("Received Request to create job");
            JobEntity job = jobService.createJob();

            log.info("Job Created Successfully with id={}", job.getId());
            return ResponseEntity
                    .status(HttpStatus.CREATED).body(ApiResponse.success(PostJobResponse.from(job)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getJob(@PathVariable Long id){

        log.info("Received Request to fetch job id={} ",id);
        Object job= jobService.getJob(id);

        if(job==null){
            log.warn("Job id={} not Found",id);
            ErrorResponse error=ErrorResponse.of(404,"Not Found","Job Not Found with id: "+id);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(error));
        }
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<CancelJobResponse>> deleteJob(@PathVariable Long id){

        log.info("Received Request to Cancel Job id={}",id);
        JobEntity cancelJob= jobService.cancelJob(id);
        return ResponseEntity.ok(ApiResponse.success(CancelJobResponse.from(cancelJob)));
    }
}