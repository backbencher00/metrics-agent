package org.metricsAgent.devicesCollectors;

import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.BackingDevice;
import org.metricsAgent.enums.DeviceType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BackingDeviceCollector {

    /**
     * Resolves all backing devices for a mount point.
     *
     * Algorithm:
     *   1. Read /proc/mounts  → logical device name (e.g. "dm-0", "sda")
     *   2. Read /sys/block/<device>/slaves  → physical members for LVM/RAID
     *   3. Classify each device by inspecting /sys/block/<device>/dm/ and /md/
     *
     * Returns list ordered: logical device first, then physical members.
     * For a simple disk the list has one entry.
     * For LVM/RAID the list has 1 + N entries (logical + N physical members).
     */
    public List<BackingDevice> resolveBackingDevices(String mountPoint) throws IOException {

        // Step 1 — find the logical device for this mount
        String rawDevice = Files.lines(Paths.get("/proc/mounts"))
                .map(line -> line.split("\\s+"))
                .filter(p -> p.length >= 2 && p[1].equals(mountPoint))
                .map(p -> p[0].replaceFirst("^/dev/", ""))
                .findFirst()
                .orElseThrow(() -> new IOException("Mount point not found in /proc/mounts: " + mountPoint));

        // For virtual mounts (tmpfs, orbstack, none) there is no block device
        if (!rawDevice.matches("[a-zA-Z].*")) {
            log.debug("Virtual device={} for mount={} — no block IO", rawDevice, mountPoint);
            return List.of(BackingDevice.builder()
                    .deviceName(rawDevice)
                    .deviceType(DeviceType.VIRTUAL)
                    .build());
        }

        // Check for both the mapper path and the direct Device Mapper name
        if (rawDevice.startsWith("mapper/") || rawDevice.startsWith("dm-")) {
            try {
                Path linkPath = Paths.get("/dev/" + rawDevice);
                if (Files.exists(linkPath)) {
                    // Resolves either path to the canonical "dm-X" name
                    rawDevice = linkPath.toRealPath().getFileName().toString();
                }
            } catch (IOException e) {
                log.warn("Failed to resolve LVM/DM path: {}", rawDevice);
            }
        }

        List<BackingDevice> result = new ArrayList<>();


        // The effective IO device is what actually appears in /proc/diskstats.
        // For a partition (vdb1), diskstats has an entry for vdb1 directly
        // since we now collect partitions. But we also record the parent (vdb)
        // so the reporter can fall back if needed.
        String effectiveIoDevice = resolveEffectiveIoDevice(rawDevice);




        // Always include the logical device first — this is what the mount sees
        result.add(BackingDevice.builder()
                .deviceName(effectiveIoDevice)
                .deviceType(classifyDevice(effectiveIoDevice))
                .build());

        // If rawDevice is a partition and differs from parent, record parent too
        // so the dashboard can show the physical disk alongside the partition
        if (!effectiveIoDevice.equals(rawDevice)) {
            result.add(BackingDevice.builder()
                    .deviceName(rawDevice)
                    .deviceType(classifyDevice(rawDevice))
                    .build());
        }

        // Step 2 — check /sys/block/<device>/slaves for physical members
        // LVM dm-0 and RAID md0 both expose their members here
        Path slavesPath = Paths.get("/sys/block/" + effectiveIoDevice + "/slaves");
        if (Files.exists(slavesPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(slavesPath)) {
                for (Path slave : stream) {
                    String physicalName = slave.getFileName().toString();
                    result.add(BackingDevice.builder()
                            .deviceName(physicalName)
                            .deviceType(classifyDevice(physicalName))
                            .build());
                }
            } catch (IOException e) {
                log.warn("Could not read slaves for device={} mount={}", effectiveIoDevice, mountPoint);
            }
        }

        return result;
    }


    /**
     * For a partition like vdb1, returns the partition itself if it appears
     * in /proc/diskstats (it will since we removed the partition skip).
     * Returns the parent disk only if the partition has no diskstats entry.
     *
     * Resolution chain: vdb1 → check /sys/block/vdb/vdb1 exists → parent is vdb
     */
    private String resolveEffectiveIoDevice(String deviceName) {
        // Already a whole disk or dm/md device — use as-is
        Path deviceSysPath = Paths.get("/sys/block/" + deviceName);
        if (Files.exists(deviceSysPath)) {
            return deviceName; // it IS a top-level block device
        }

        // It's a partition — find its parent by scanning /sys/block/
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("/sys/block"))) {
            for (Path parentPath : stream) {
                String parentName = parentPath.getFileName().toString();
                // If /sys/block/vdb/vdb1 exists, parent is vdb
                if (Files.exists(parentPath.resolve(deviceName))) {
                    log.debug("Partition {} belongs to parent disk {}", deviceName, parentName);
                    // Return the partition — diskstats now tracks it directly
                    // Parent is returned for reference only (second entry in list)
                    return deviceName;
                }
            }
        } catch (IOException e) {
            log.warn("Could not resolve parent for device={}", deviceName);
        }

        return deviceName;
    }

    /**
     * Classifies a device by inspecting /sys/block/<device>/:
     *   /sys/block/dm-0/dm/   → LVM or MULTIPATH (check dm/uuid prefix)
     *   /sys/block/md0/md/    → RAID
     *   otherwise             → SIMPLE
     */
    private DeviceType classifyDevice(String deviceName) {
        try {
            // Device-mapper: LVM or multipath
            Path dmPath = Paths.get("/sys/block/" + deviceName + "/dm");
            if (Files.exists(dmPath)) {
                // Read the dm uuid — multipath uuids start with "mpath-"
                Path uuidPath = dmPath.resolve("uuid");
                if (Files.exists(uuidPath)) {
                    String uuid = Files.readString(uuidPath).trim();
                    return uuid.startsWith("mpath-")
                            ?  DeviceType.MULTIPATH
                            :  DeviceType.LVM;
                }
                return DeviceType.LVM;
            }


            // Software RAID
            Path mdPath = Paths.get("/sys/block/" + deviceName + "/md");
            if (Files.exists(mdPath)) {
                return DeviceType.RAID;
            }

        } catch (IOException e) {
            log.warn("Could not classify device={}", deviceName);
        }

        return DeviceType.SIMPLE;
    }
}