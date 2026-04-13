package org.metricsAgent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.response.MetricsAgentResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsServiceClient {

    private final WebClient metricsWebClient;
    private final ObjectMapper objectMapper;

    private static final String INGEST_PATH = "/ingest";

    public void send(MetricsAgentResponse payload) {
        payload.setSentAt(System.currentTimeMillis());

        try {
            // Use the injected mapper for debug logging
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.debug("Payload to send: \n{}", json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON for debugging", e);
        }

        // Note: WebClient uses its own HttpMessageWriter to serialize 'payload'.
        // It does not use the string you just printed above.
        metricsWebClient.post()
                .uri(INGEST_PATH)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Backend rejected [{}]: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("4xx error"));
                                })
                )
                .onStatus(status -> status.is5xxServerError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.warn("Backend error [{}]: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("5xx error"));
                                })
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v ->
                        log.debug("Metrics sent: seq={} surgeTriggered={}",
                                payload.getSequenceNumber(), payload.isSurgeTriggered())
                )
                .doOnError(e ->
                        log.error("Network error sending metrics", e)
                )
                .subscribe(); // Fires the async call in the background
    }
}