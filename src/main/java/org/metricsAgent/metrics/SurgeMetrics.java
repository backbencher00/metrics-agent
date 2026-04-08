package org.metricsAgent.metrics;

import lombok.Builder;
import lombok.Data;

/**
 * Surge metrics computed ENTIRELY at the agent level on the VM.
 *
 * Source: /proc/diskstats — two reads 1s apart (t vs t-1).
 *
 * Agent logic:
 *   deltaReadsPerSec  = reads_completed(t)  - reads_completed(t-1)
 *   deltaWritesPerSec = writes_completed(t) - writes_completed(t-1)
 *   surgeRate         = currentDelta / rollingBaseline (60s window)
 *   isSurge           = surgeRate >= surgeThreshold (default 2.0×)
 *
 * Backend never sees raw cumulative counters — only this computed object.
 */
@Data
@Builder
public class SurgeMetrics {

    /** True when surgeRate >= surgeThreshold. Set by agent. */
    private boolean isSurge;

    /**
     * Ratio of current 1s delta to the rolling 60s baseline.
     * e.g. 3.4 means current IOPS is 3.4× the recent average.
     * Values below 1.0 mean below-baseline IO (idle/quiet).
     */
    private double surgeRate;

    /**
     * Threshold at which isSurge flips to true.
     * Default: 2.0 — configurable per device in agent config.
     */
    private double surgeThreshold;

    /** Read IOPS computed from t vs t-1 delta of reads_completed field. */
    private long deltaReadsPerSec;

    /** Write IOPS computed from t vs t-1 delta of writes_completed field. */
    private long deltaWritesPerSec;

    /**
     * Rolling 60-second average read IOPS — the baseline denominator.
     * Agent maintains a circular buffer of 60 1-second samples.
     */
    private double rollingBaselineReadsPerSec;

    /**
     * Rolling 60-second average write IOPS — the baseline denominator.
     */
    private double rollingBaselineWritesPerSec;

    /**
     * Read throughput in bytes/sec — from sectors_read * 512 delta.
     * (Each sector in /proc/diskstats is always 512 bytes regardless of physical sector size.)
     */
    private long readThroughputBytesPerSec;

    /** Write throughput in bytes/sec — from sectors_written * 512 delta. */
    private long writeThroughputBytesPerSec;

    /** Epoch ms when this surge snapshot was computed by the agent. */
    private long surgeComputedAt;
}