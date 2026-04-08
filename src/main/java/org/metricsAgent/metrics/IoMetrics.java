package org.metricsAgent.metrics;

import lombok.Builder;
import lombok.Data;

/**
 * Per-device IO metrics derived from /proc/diskstats delta.
 *
 * /proc/diskstats column mapping (kernel 2.6+):
 *   col 1  = major, col 2 = minor, col 3 = device name
 *   col 4  = reads_completed
 *   col 5  = reads_merged
 *   col 6  = sectors_read
 *   col 7  = time_reading_ms       → used for read latency
 *   col 8  = writes_completed
 *   col 9  = writes_merged
 *   col 10 = sectors_written
 *   col 11 = time_writing_ms       → used for write latency
 *   col 12 = io_in_progress        → queue depth (instantaneous)
 *   col 13 = time_doing_io_ms      → IO utilisation
 *
 * All rates are per-second deltas computed by the agent.
 */
@Data
@Builder
public class IoMetrics {

    // ── IOPS ─────────────────────────────────────────────────────────────────

    /** Read IOPS = delta(reads_completed) / interval_sec */
    private long readIops;

    /** Write IOPS = delta(writes_completed) / interval_sec */
    private long writeIops;

    /** Total IOPS = readIops + writeIops */
    private long totalIops;

    // ── LATENCY ──────────────────────────────────────────────────────────────

    /**
     * Average read latency in ms.
     * = delta(time_reading_ms) / delta(reads_completed)
     * Returns 0 when no reads occurred in the interval.
     */
    private double readLatencyMs;

    /**
     * Average write latency in ms.
     * = delta(time_writing_ms) / delta(writes_completed)
     */
    private double writeLatencyMs;

    // ── QUEUE ────────────────────────────────────────────────────────────────

    /**
     * Instantaneous IO queue depth.
     * Direct value of io_in_progress (col 12) — not a delta.
     * Values > 4 indicate backpressure; > 8 is a warning threshold.
     */
    private int queueDepth;

    /**
     * IO utilisation percentage (0–100).
     * = delta(time_doing_io_ms) / (interval_ms) * 100
     * 100% means the device was busy the entire interval.
     */
    private double ioUtilisationPct;

    // ── THROUGHPUT ───────────────────────────────────────────────────────────

    /**
     * Read throughput in bytes/sec.
     * = delta(sectors_read) * 512 / interval_sec
     */
    private long readThroughputBytesPerSec;

    /**
     * Write throughput in bytes/sec.
     * = delta(sectors_written) * 512 / interval_sec
     */
    private long writeThroughputBytesPerSec;
}