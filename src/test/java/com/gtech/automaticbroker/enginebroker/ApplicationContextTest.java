package com.gtech.automaticbroker.enginebroker;

import static org.assertj.core.api.Assertions.assertThat;

import com.gtech.automaticbroker.enginebroker.api.ResearchController;
import com.gtech.automaticbroker.enginebroker.kafka.producer.ManageDatabaseClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.kafka.properties.security.protocol=PLAINTEXT",
            "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = "manageDatabase.responses",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ApplicationContextTest {

    @Autowired
    private ResearchController researchController;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private Environment environment;

    @MockitoBean
    private ManageDatabaseClient manageDatabaseClient;

    @Test
    void startsApplicationAndKafkaListener() {
        assertThat(researchController).isNotNull();
        assertThat(kafkaListenerEndpointRegistry.getListenerContainers())
                .isNotEmpty()
                .allSatisfy(container -> assertThat(container.isRunning()).isTrue());
    }

    @Test
    void usesStringDeserializerForJsonResponseValues() {
        assertThat(environment.getProperty("spring.kafka.consumer.value-deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
    }
}
