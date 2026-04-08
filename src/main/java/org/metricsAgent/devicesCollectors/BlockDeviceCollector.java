package org.metricsAgent.devicesCollectors;

import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.UnmountedDevice;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * collect the devices which are not the part of the LVM/storage pool
 * they are unmounted disks
 */
@Slf4j
@Service
public class BlockDeviceCollector {


    /**Device name     Old pattern (.*\d+)    New pattern (ram\d+|loop\d+|zram\d+)
            ─────────────   ────────────────────   ─────────────────────────────────────
    sda             kept ✓                 kept ✓
    sda1            BLOCKED ✗ ← bug        kept ✓
    nvme0n1         BLOCKED ✗ ← bug        kept ✓
    nvme0n1p1       kept ✓ (accident)      kept ✓
    ram0            kept ✓ (should skip)   skipped ✓
    loop0           kept ✓ (should skip)   skipped ✓
    zram0           kept ✓ (should skip)   skipped ✓
     */
    private static final String SKIP_PATTERN = "ram\\d+|loop\\d+|zram\\d+";


    /**
     * Runs: lsblk -b -o NAME,SIZE,TYPE,MOUNTPOINT,FSTYPE,ROTA -J
     * Cross-references with /proc/mounts to classify devices.
     */
    public List<UnmountedDevice> collectUnmounted() throws IOException {

        // Step 1 — get all mounted device names from /proc/mounts
        Set<String> mountedDeviceNames = Files.lines(Paths.get("/proc/mounts"))
                .map(line -> line.split("\\s+")[0])
                .map(dev -> dev.replaceFirst("/dev/", ""))
                .collect(Collectors.toSet());

        // Step 2 — get all block devices from lsblk
        // Parse /sys/block directly — no process spawn needed
        List<UnmountedDevice> unmounted = new ArrayList<>();

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(Paths.get("/sys/block"))) {

            for (Path devicePath : stream) {
                String deviceName = devicePath.getFileName().toString();

                // Skip virtual devices — same pattern as IoMetricsCollector
                if (deviceName.matches(".*\\d+|ram\\d+|loop\\d+|dm-\\d+")) continue;


                // Fix: a disk (vdb) is "effectively mounted" if any of its
                // partitions (vdb1, vdb2) appear in /proc/mounts
                if (isEffectivelyMounted(deviceName, devicePath, mountedDeviceNames)) continue;

                long    sizeBytes   = readSysBlockLong(deviceName, "size") * 512L;
                boolean rotational  = readSysBlockLong(deviceName, "queue/rotational") == 1;
                String  fsType     = detectFilesystemType(deviceName);


                unmounted.add(UnmountedDevice.builder()
                        .deviceName(deviceName)
                        .devicePath("/dev/" + deviceName)
                        .sizeBytes(sizeBytes)
                        .rotational(rotational)
                        .storageType(rotational ? "hdd" : "ssd")
                        .unformatted(fsType == null)
                        .firstSeenAt(System.currentTimeMillis())
                        .collectedAt(System.currentTimeMillis())
                        .build());
            }
        }

        return unmounted;
    }

    private long readSysBlockLong(String device, String attribute) {
        try {
            String val = Files.readString(
                    Paths.get("/sys/block/" + device + "/" + attribute)).trim();
            return Long.parseLong(val);
        } catch (Exception e) {
            log.warn("Could not read /sys/block/{}/{}", device, attribute);
            return 0L;
        }
    }

    /**
     * Reads filesystem type from /sys/block/<device>/uevent (DEVTYPE, ID_FS_TYPE).
     * Falls back to null (= unformatted) if not detectable without blkid.
     */
    private String detectFilesystemType(String deviceName) {
        try {
            Path uevent = Paths.get("/sys/block/" + deviceName + "/uevent");
            if (Files.exists(uevent)) {
                return Files.lines(uevent)
                        .filter(l -> l.startsWith("ID_FS_TYPE="))
                        .map(l -> l.split("=", 2)[1])
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            log.debug("Could not detect fsType for device={}", deviceName);
        }
        return null;
    }
    /**
     * A disk is effectively mounted if:
     *   (a) the disk itself is in /proc/mounts (e.g. whole-disk formatted)
     *   (b) any partition under /sys/block/<disk>/<partition> is in /proc/mounts
     *
     * Without this, vdb shows as unmounted even though vdb1 is mounted.
     */
    private boolean isEffectivelyMounted(
            String deviceName,
            Path   deviceSysPath,
            Set<String> mountedNames) {

        // Direct match — whole disk is mounted
        if (mountedNames.contains(deviceName)) return true;

        // Check partitions: /sys/block/vdb/vdb1, /sys/block/vdb/vdb2, ...
        try (DirectoryStream<Path> children = Files.newDirectoryStream(deviceSysPath,
                entry -> entry.getFileName().toString().startsWith(deviceName))) {
            for (Path child : children) {
                if (mountedNames.contains(child.getFileName().toString())) {
                    return true; // at least one partition is mounted
                }
            }
        } catch (IOException e) {
            log.warn("Could not check partitions for device={}", deviceName);
        }

        return false;
    }

}