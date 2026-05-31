package com.savi.jobprocessor.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {


    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final String GROUP_ID = "job-processor-group";

    @Bean
    public NewTopic jobRequestsTopic() {
        //the new topic is created only when there are none, if present kafka use the same topics
        return TopicBuilder.name("job-requests")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ─── PRODUCER ─────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Long> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,  LongSerializer.class);

        // BUG FIX: Idempotent producer config
        // ENABLE_IDEMPOTENCE: producer assigns sequence numbers to each message.
        // The broker detects and drops duplicate sends caused by retries.
        // This is Kafka's "exactly-once producer" guarantee.
        // Requires acks=all — they must be set together.
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retries: idempotence is only useful if the producer actually retries.
        // Default is already MAX_INT in Kafka 3.x, but being explicit is better.
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        config.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
                org.apache.kafka.clients.producer.RoundRobinPartitioner.class);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 0);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Long> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ─── CONSUMER ─────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Long> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,           GROUP_ID);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,  LongDeserializer.class);
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                org.apache.kafka.clients.consumer.RoundRobinAssignor.class.getName());

        return new DefaultKafkaConsumerFactory<>(config);
    }


    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Long> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Long> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}