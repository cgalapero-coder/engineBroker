package com.gtech.automaticbroker.enginebroker.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    @Bean(destroyMethod = "close")
    AdminClient kafkaHealthAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @Bean
    DefaultErrorHandler databaseResponseErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            DatabaseMessagingProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.deadLetterSuffix(),
                        record.partition()));
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.retryIntervalMs(), properties.maxRetries()));
    }
}
