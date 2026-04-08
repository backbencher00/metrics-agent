package org.metricsAgent.LVMstoragePool;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MountCapacity {

    /**
     * Total size in bytes (lsblk -b raw value for precision)
     */
    private long totalSizeBytes;

    /**
     * Used space in bytes
     */
    private long usedBytes;

    /**
     * Available space in bytes
     */
    private long availableBytes;

    /**
     * Disk usage percentage (0–100).
     * Thresholds: warn >= 70%, critical >= 85%.
     */
    private double usagePct;

    private long reservedBytes;

}