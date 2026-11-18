package com.gtech.automaticbroker.enginebroker;

import static org.assertj.core.api.Assertions.assertThat;

import com.gtech.automaticbroker.enginebroker.api.ResearchController;
import com.gtech.automaticbroker.enginebroker.kafka.producer.ManageDatabaseClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
        partitions = 1,
        topics = "manageDatabase.responses",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ApplicationContextTest {

    @Autowired
    private ResearchController researchController;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockitoBean
    private ManageDatabaseClient manageDatabaseClient;

    @Test
    void startsApplicationAndKafkaListener() {
        assertThat(researchController).isNotNull();
        assertThat(kafkaListenerEndpointRegistry.getListenerContainers())
                .isNotEmpty()
                .allSatisfy(container -> assertThat(container.isRunning()).isTrue());
    }
}
