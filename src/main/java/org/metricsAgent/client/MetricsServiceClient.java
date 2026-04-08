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

    private static final String INGEST_PATH = "/metrics/ingest";

    public void send(MetricsAgentResponse payload) throws JsonProcessingException {
        /**
         * add sent at before actually sending the metrics
         */
        payload.setSentAt(System.currentTimeMillis());
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        System.out.println(json);
       /* metricsWebClient.post()
                .uri(INGEST_PATH)
                .bodyValue(payload)   // ✅ cleaner
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Backend rejected [{}]: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("4xx error")); // ✅ fix
                                })
                )
                .onStatus(status -> status.is5xxServerError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.warn("Backend error [{}]: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("5xx error")); // ✅ fix
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
                .subscribe(); // fire-and-forget
                */

    }
}