package com.savi.jobprocessor.controller;

import com.savi.jobprocessor.entity.JobEntity;
import com.savi.jobprocessor.ratelimit.RateLimitExceededException;
import com.savi.jobprocessor.ratelimit.RateLimiterService;
import com.savi.jobprocessor.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JobService jobService;

    @MockBean
    RateLimiterService rateLimiterService;

    @Test
    void createJob_shouldReturn201_withSuccessResponse() throws Exception {

        JobEntity job = new JobEntity();
        job.setId(1L);

        when(jobService.createJob()).thenReturn(job);

        mockMvc.perform(post("/jobs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void createJob_shouldReturn500_whenServiceFails() throws Exception {

        when(jobService.createJob())
                .thenThrow(new RuntimeException("error"));

        mockMvc.perform(post("/jobs"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void createJob_shouldReturn429_whenRateLimited() throws Exception {

        doThrow(new RateLimitExceededException("Too many requests"))
                .when(rateLimiterService).validateCreateJobRequest(any());

        mockMvc.perform(post("/jobs"))
                .andExpect(status().isTooManyRequests());
    }
}