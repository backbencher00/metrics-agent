package org.metricsAgent.LVMstoragePool;

import lombok.Builder;
import lombok.Data;
import org.metricsAgent.enums.DeviceType;
import org.metricsAgent.metrics.IoMetrics;
import org.metricsAgent.metrics.SurgeMetrics;

/**
 * **It represents a physical volume in a LVM**
 * One block device that contributes IO to a mount.
 * Carries its own IO metrics because in RAID/LVM each
 * physical disk has independent throughput and latency.
 */
@Data
@Builder
public class BackingDevice {

    private String     deviceName;   // "sda", "md0", "dm-0"
    private DeviceType deviceType;   // see enum below

    /** IO rates from /proc/diskstats for this specific device */
    private IoMetrics ioMetrics;

    /** Surge state on rolling 60s window for this device */
    private SurgeMetrics surgeMetrics;
}

 
