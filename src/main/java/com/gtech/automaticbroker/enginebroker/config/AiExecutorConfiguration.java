package com.gtech.automaticbroker.enginebroker.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService researchAiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
