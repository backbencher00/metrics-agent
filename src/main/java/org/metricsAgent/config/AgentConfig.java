package org.metricsAgent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent-config")
@Getter
@Setter
public class AgentConfig {

    private String orgId;
    private String clientId;
    private String vmId;
    private String agentId;

    private Monitor monitor;

    @Getter
    @Setter
    public static class Monitor {
        private List<String> mountPoints;
    }
}