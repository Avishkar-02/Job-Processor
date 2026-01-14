package com.savi.jobprocessor.repository;

import com.savi.jobprocessor.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<JobEntity,Long> {
}
