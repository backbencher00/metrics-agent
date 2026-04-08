package org.metricsAgent.LVMstoragePool;

import lombok.Builder;
import lombok.Data;
import org.metricsAgent.metrics.InodeMetrics;

import java.util.List;

/**
 * This is our logical  volume Node
 * Everything about one mount point.
 * The device fields are a reference — they tell you where the IO
 * numbers came from, not a parent object you navigate into.
 */
@Data
@Builder
public class MountDetail {

    // ── MOUNT IDENTITY ────────────────────────────────────────────────────────
    private String  mountPoint;   // "/data"
    private String  fsType;       // "ext4", "xfs"
    private boolean readOnly;
    /**
     * True for Azure ephemeral temp disk (/mnt/resource or /mnt).
     * Data on this mount is LOST when the VM is stopped or deallocated.
     * Backend must never use capacity figures here for planning decisions.
     */
    private boolean ephemeral;


    // ── CAPACITY + INODES — always per mount ──────────────────────────────────
    private MountCapacity mountCapacity;
    private InodeMetrics inodes;

    /**
     * One entry per physical/logical device backing this mount.
     *
     * Simple disk:  1 entry  (sda)
     * RAID/LVM:     N entries (sda, sdb, ...)
     * Multi-path:   N entries (same LUN, different paths)
     */
    private List<BackingDevice> backingDevices;



    /** Epoch ms when this mount snapshot was collected by the agent */
    private long collectedAt;
}
