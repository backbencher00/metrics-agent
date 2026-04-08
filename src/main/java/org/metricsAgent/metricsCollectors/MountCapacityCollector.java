package org.metricsAgent.metricsCollectors;

import org.metricsAgent.metrics.InodeMetrics;
import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.MountCapacity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MountCapacityCollector {

    /**
     * Uses FileStore (statvfs) for ALL filesystem types including btrfs,
     * tmpfs, and virtual mounts. File.getTotalSpace() returns 0 for many
     * of these — FileStore never does.
     */
    public MountCapacity collectCapacity(String path) {
        try {
            FileStore store = Files.getFileStore(Paths.get(path));

            long total    = store.getTotalSpace();

            // btrfs subvolumes without quotas return 0 from statvfs.
            // Fall back to a sibling mount of the same block device.
            if(total == 0){

                log.debug("Zero capacity from statvfs on path={} — trying sibling mount fallback", path);
                return collectCapacityViaSiblingMount(path);

            }

            long free     = store.getUnallocatedSpace();  // includes root-reserved
            long usable   = store.getUsableSpace();
            long used     = total - usable;               // mirrors df
            long reserved = free  - usable;               // root-reserved blocks (~5%)
            double usagePct = (total > 0) ? ((double) used / total) * 100.0 : 0.0;


            return MountCapacity.builder()
                    .totalSizeBytes(total)
                    .usedBytes(used)
                    .availableBytes(usable)
                    .reservedBytes(reserved)
                    .usagePct(usagePct)
                    .build();
        }catch (IOException e){
            log.warn("Capacity collection failed for path={} reason={}", path, e.getMessage());
            return MountCapacity.builder().build();
        }

    }

    /**
     * Uses FileStore NIO API — equivalent to statvfs() syscall.
     * No shell, no OSHI, no JNA. Works on any Linux kernel 2.6+.
     *
     * Critical for container environments: a mount can exhaust inodes
     * while disk % looks healthy. This catches that silently failing case.
     *
     * btrfs and some virtual FSes report 0 inodes — treated as non-fatal.
     * The warn log tells you which mounts don't support inode tracking.
     *
     */
    public InodeMetrics collectInodes(String path) {
        try {
            FileStore store = Files.getFileStore(Paths.get(path));

            // These attributes map directly to statvfs.f_files / f_ffree / f_favail
            long total     = (long) store.getAttribute("unix:totalInodeCount");
            long free      = (long) store.getAttribute("unix:freeInodeCount");
            long available = (long) store.getAttribute("unix:availableInodeCount");
            long used      = total - free;
            double usagePct = (total > 0) ? ((double) used / total) * 100.0 : 0.0;

            return InodeMetrics.builder()
                    .totalInodes(total)
                    .usedInodes(used)
                    .availableInodes(available)
                    .inodeUsagePct(usagePct)
                    .build();

        } catch (UnsupportedOperationException e) {
            // Let orchestrator handle — it will log at debug and store empty InodeMetrics
            throw e;
        } catch (IOException e) {
            // Non-fatal: some pseudo-filesystems (tmpfs) report 0 inodes
            log.warn("Inode collection failed for path={} reason={}", path, e.getMessage());
            return InodeMetrics.builder().build();
        }
    }


    /**
     * For btrfs subvolumes without quotas, statvfs returns 0.
     * Strategy: find the block device for this mount, then find any other
     * mount of the same device that returns non-zero stats.
     *
     * Example: /mnt/machines/c1-vm-1 is btrfs subvolume of /dev/vdb1.
     *          /dev/vdb1 is also mounted at /  which returns real stats.
     *          We use / to get the shared pool's total/used/available.
     */
    private MountCapacity collectCapacityViaSiblingMount(String mountPath) throws IOException {

        // Step 1 — find which block device backs this mount
        String blockDevice = Files.lines(Paths.get("/proc/mounts"))
                .map(line -> line.split("\\s+"))
                .filter(p -> p.length >= 2 && p[1].equals(mountPath))
                .map(p -> p[0])
                .findFirst()
                .orElse(null);

        if (blockDevice == null || !blockDevice.startsWith("/dev/")) {
            log.debug("No block device found for mount={}", mountPath);
            return MountCapacity.builder().build();
        }

        // Step 2 — find all other mounts of the same block device
        List<String> siblingMounts = Files.lines(Paths.get("/proc/mounts"))
                .map(line -> line.split("\\s+"))
                .filter(p -> p.length >= 2
                        && p[0].equals(blockDevice)
                        && !p[1].equals(mountPath))
                .map(p -> p[1])
                .toList();

        // Step 3 — try each sibling until we get non-zero stats
        for (String sibling : siblingMounts) {
            try {
                FileStore siblingStore = Files.getFileStore(Paths.get(sibling));
                long total = siblingStore.getTotalSpace();

                if (total > 0) {
                    long free     = siblingStore.getUnallocatedSpace();
                    long usable   = siblingStore.getUsableSpace();
                    long used     = total - usable;
                    long reserved = free - usable;
                    double usagePct = ((double) used / total) * 100.0;

                    log.debug("Resolved capacity for mount={} via sibling={} total={}",
                            mountPath, sibling, total);

                    return MountCapacity.builder()
                            .totalSizeBytes(total)
                            .usedBytes(used)
                            .availableBytes(usable)
                            .reservedBytes(reserved)
                            .usagePct(usagePct)
                            .build();
                }
            } catch (IOException e) {
                log.debug("Sibling mount={} also unreadable, trying next", sibling);
            }
        }

        log.warn("All sibling mounts of device={} returned 0 for mount={}", blockDevice, mountPath);
        return MountCapacity.builder().build();
    }
}