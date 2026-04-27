package com.savi.jobprocessor.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class JobKafkaProducer {

    private static final String TOPIC = "job-requests";
    private static final Logger log = LoggerFactory.getLogger(JobKafkaProducer.class);

    private final KafkaTemplate<String, Long> kafkaTemplate;

    public JobKafkaProducer(KafkaTemplate<String, Long> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishJob(Long jobId) {
        kafkaTemplate.send(TOPIC, jobId)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish job id={} to Kafka", jobId, ex);
                    } else {
                        log.info("Job id={} published to Kafka topic={} partition={}",
                                jobId, TOPIC,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}