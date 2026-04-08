package org.metricsAgent.enums;

public enum DeviceType {
    SIMPLE,      // plain disk or partition — sda, nvme0n1
    RAID,        // Linux software RAID — md0, md1
    LVM,         // logical volume — dm-0, dm-1
    MULTIPATH,   // same LUN via multiple HBAs — dm-0 via mpathX
    BCACHE,      // SSD-cached HDD
    UNKNOWN,
    VIRTUAL     // tmpfs, orbstack, none — no block IO
}