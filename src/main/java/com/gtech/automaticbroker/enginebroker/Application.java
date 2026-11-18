package com.gtech.automaticbroker.enginebroker;

import com.gtech.automaticbroker.enginebroker.config.DatabaseMessagingProperties;
import com.gtech.automaticbroker.enginebroker.config.ResearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({DatabaseMessagingProperties.class, ResearchProperties.class})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
