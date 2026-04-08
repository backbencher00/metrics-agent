package org.metricsAgent.response;

import lombok.Builder;
import lombok.Data;
import org.metricsAgent.LVMstoragePool.MountDetail;
import org.metricsAgent.LVMstoragePool.UnmountedDevice;

import java.util.List;
/**
 * Complete VM storage snapshot sent to the backend every 30s.
 *
 * Two sections:
 *   mounts          — every device in /proc/mounts with full detail
 *   unmountedDevices — every device in lsblk but NOT in /proc/mounts
 *
 * VM-wide aggregates are pre-computed here so the backend can do
 * threshold evaluation in O(1) without walking the device list.
 */

@Data
@Builder
public class VmMetrics {

    // ── IDENTITY ──────────────────────────────────────────────────────────────
    private String vmId;
    private String hostname;
    private long   collectedAt;
    private String agentVersion;

    // ── MOUNTED — full detail ─────────────────────────────────────────────────
    /** One entry per active mount point — the primary payload. */
    private List<MountDetail> mounts;


    // ── UNMOUNTED — attached but not in /proc/mounts ──────────────────────────
    /**
     * Devices visible in lsblk with no corresponding mount point.
     * Backend should alert if unformatted devices persist across N cycles.
     * these are the physical devices which is not the part of any LVM layer
     */
    private List<UnmountedDevice> unmountedDevices;

    // ── VM-WIDE AGGREGATES ────────────────────────────────────────────────────
    private long totalReadIops;
    private long totalWriteIops;
    private long totalReadThroughputBytesPerSec;
    private long totalWriteThroughputBytesPerSec;
    private long totalUsedBytes;
    private long totalAvailableBytes;

    // mount counters
    private int totalMounts;
    private int readOnlyMountCount;
    private int criticalInodeMountCount;
    private int surgingMountCount;

    // unmounted counters — backend uses these for fast threshold checks
    private int unmountedDeviceCount;
    private int unformattedDeviceCount;  // subset of unmounted — needs provisioning
}