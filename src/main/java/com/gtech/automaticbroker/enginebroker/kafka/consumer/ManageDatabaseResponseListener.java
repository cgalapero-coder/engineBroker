package com.gtech.automaticbroker.enginebroker.kafka.consumer;

import com.gtech.automaticbroker.enginebroker.kafka.producer.ManageDatabaseClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ManageDatabaseResponseListener {

    private final ManageDatabaseClient manageDatabaseClient;

    public ManageDatabaseResponseListener(ManageDatabaseClient manageDatabaseClient) {
        this.manageDatabaseClient = manageDatabaseClient;
    }

    @KafkaListener(topics = "${app.manage-database.response-topic}")
    public void receive(String rawMessage) {
        manageDatabaseClient.acceptResponse(rawMessage);
    }
}
