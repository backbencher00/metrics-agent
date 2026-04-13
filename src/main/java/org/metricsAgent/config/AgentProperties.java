package org.metricsAgent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds to the 'agent' prefix in application.yml.
 * These values can be overridden via command-line args:
 *   --agent.agent-version=1.0.3
 *   --agent.poll-interval-ms=10000
 */
@Component
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class AgentProperties {

    private String agentVersion = "1.0.3";
    private long lastAtomicNumberSeen = 0;
    private int pollIntervalMs = 10000;
}
