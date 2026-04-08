package org.metricsAgent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@ComponentScan("org.metricsAgent")
@EnableScheduling
public class SpmAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpmAgentApplication.class, args);
    }
}