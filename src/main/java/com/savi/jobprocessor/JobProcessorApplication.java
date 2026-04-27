package com.savi.jobprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class JobProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobProcessorApplication.class, args);
	}

}
