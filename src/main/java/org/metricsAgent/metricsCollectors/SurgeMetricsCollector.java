package org.metricsAgent.metricsCollectors;

import org.metricsAgent.metrics.SurgeMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class SurgeMetricsCollector {

    private static final int    BASELINE_WINDOW_SEC = 60;
    private static final double DEFAULT_SURGE_THRESHOLD = 2.0;
    private static final int    SECTOR_SIZE_BYTES = 512;

    // Circular buffers — one per device, 60 slots (1 per second)
    private final Deque<Long> readSamples  = new ArrayDeque<>(BASELINE_WINDOW_SEC);
    private final Deque<Long> writeSamples = new ArrayDeque<>(BASELINE_WINDOW_SEC);

    private final double surgeThreshold;

    public SurgeMetricsCollector() {
        this(DEFAULT_SURGE_THRESHOLD);
    }

    public SurgeMetricsCollector(double surgeThreshold) {
        this.surgeThreshold = surgeThreshold;
    }

    /**
     * Call once per second with t and t-1 snapshots for a single device.
     * Maintains its own 60s rolling window internally.
     */
    public SurgeMetrics compute(IoMetricsCollector.IoRawSnapshot curr, IoMetricsCollector.IoRawSnapshot prev) {
        long deltaReads  = Math.max(0, curr.getReads()          - prev.getReads());
        long deltaWrites = Math.max(0, curr.getWrites()         - prev.getWrites());
        long readBytes   = Math.max(0, curr.getSectorsRead()    - prev.getSectorsRead())    * SECTOR_SIZE_BYTES;
        long writeBytes  = Math.max(0, curr.getSectorsWritten() - prev.getSectorsWritten()) * SECTOR_SIZE_BYTES;

        // Slide the window
        addSample(readSamples,  deltaReads);
        addSample(writeSamples, deltaWrites);

        double baselineReads  = average(readSamples);
        double baselineWrites = average(writeSamples);

        // Surge rate = current delta / rolling average
        // Use combined read+write so a write-only surge still triggers
        double currentTotal  = deltaReads + deltaWrites;
        double baselineTotal = baselineReads + baselineWrites;

        // Avoid false surges during idle→active transition (baseline near zero)
        double surgeRate = (baselineTotal > 1.0) ? currentTotal / baselineTotal : 0.0;
        boolean isSurge  = surgeRate >= surgeThreshold;

        if (isSurge) {
            log.warn("IO surge detected: surgeRate={:.2f}x reads/s={} writes/s={}",
                    surgeRate, deltaReads, deltaWrites);
        }

        return SurgeMetrics.builder()
                .isSurge(isSurge)
                .surgeRate(surgeRate)
                .surgeThreshold(surgeThreshold)
                .deltaReadsPerSec(deltaReads)
                .deltaWritesPerSec(deltaWrites)
                .rollingBaselineReadsPerSec(baselineReads)
                .rollingBaselineWritesPerSec(baselineWrites)
                .readThroughputBytesPerSec(readBytes)
                .writeThroughputBytesPerSec(writeBytes)
                .surgeComputedAt(System.currentTimeMillis())
                .build();
    }

    private void addSample(Deque<Long> window, long value) {
        if (window.size() >= BASELINE_WINDOW_SEC) window.pollFirst();
        window.addLast(value);
    }

    private double average(Deque<Long> window) {
        return window.isEmpty() ? 0.0 : window.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
}