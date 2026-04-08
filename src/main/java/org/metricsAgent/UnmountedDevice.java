package org.metricsAgent;

import lombok.Builder;
import lombok.Data;
import org.metricsAgent.enums.DeviceType;
import org.metricsAgent.metrics.IoMetrics;

@Data
@Builder
public class UnmountedDevice {

    // ── IDENTITY ──────────────────────────────────────────────────────────────
    /** e.g. "sdc", "nvme1n1" */
    private String deviceName;

    /** Full kernel path — "/dev/sdc" */
    private String devicePath;

    private DeviceType deviceType;  // SIMPLE, RAID, LVM, etc.

    // ── PHYSICAL PROPERTIES ───────────────────────────────────────────────────
    /** Total size in bytes from lsblk */
    private long sizeBytes;

    /** true = HDD (rotational), false = SSD/NVMe */
    private boolean rotational;

    /** "ssd", "hdd", "nvme" — from lsblk or /sys/block/sdc/queue/rotational */
    private String storageType;

    // ── STATE ─────────────────────────────────────────────────────────────────
    /**
     * Whether the device has a filesystem on it.
     * Detected via lsblk FSTYPE column.
     * null  = never formatted (raw block device)
     * "ext4", "xfs" etc. = formatted but not mounted
     */
    private String filesystemType;

    /**
     * Unformatted means no filesystem — data cannot be written without mkfs.
     * Useful for distinguishing "ready to mount" from "needs provisioning".
     */
    private boolean unformatted;

    // ── IO (best-effort) ──────────────────────────────────────────────────────
    /**
     * IO metrics are still collected from /proc/diskstats even for
     * unmounted devices — a device can have IO if accessed as raw block
     * device (e.g. database direct IO, backup agent, dd).
     *
     * null if the device has never appeared in /proc/diskstats.
     */
    private IoMetrics ioMetrics;

    /** Epoch ms when this device was first seen by the agent */
    private long firstSeenAt;

    /** Epoch ms of this snapshot */
    private long collectedAt;
}
