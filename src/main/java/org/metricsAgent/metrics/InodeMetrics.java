package org.metricsAgent.metrics;

import lombok.Builder;
import lombok.Data;

/**
 * Inode usage per mount point.
 *
 * Source: df -i --output=target,itotal,iused,iavail,ipcent
 *
 * Critical: a mount can hit 100% inode exhaustion while disk space is still
 * available (e.g. many small files, container log dirs). When inodes are
 * exhausted, writes fail with "No space left on device" even at 40% usage.
 */
@Data
@Builder
public class InodeMetrics {

    /** Total inodes allocated for this mount. */
    private long totalInodes;

    /** Inodes currently in use. */
    private long usedInodes;

    /** Inodes still available. */
    private long availableInodes;

    /**
     * Inode usage as a percentage (0–100).
     * Thresholds: warn >= 70%, critical >= 85%, exhausted = 100%.
     */
    private double inodeUsagePct;
}