package com.savi.jobprocessor.repository;

import com.savi.jobprocessor.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<JobEntity,Long> {
}
