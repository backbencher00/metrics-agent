package org.metricsAgent.service;

import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.BackingDevice;
import org.metricsAgent.LVMstoragePool.MountCapacity;
import org.metricsAgent.LVMstoragePool.MountDetail;
import org.metricsAgent.LVMstoragePool.UnmountedDevice;
import org.metricsAgent.client.MetricsServiceClient;
import org.metricsAgent.config.AgentConfig;
import org.metricsAgent.devicesCollectors.BackingDeviceCollector;
import org.metricsAgent.devicesCollectors.BlockDeviceCollector;
import org.metricsAgent.enums.DeviceType;
import org.metricsAgent.metrics.InodeMetrics;
import org.metricsAgent.metrics.IoMetrics;
import org.metricsAgent.metrics.SurgeMetrics;
import org.metricsAgent.response.MetricsAgentResponse;
import org.metricsAgent.response.VmMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetricsReporter {

    @Value("${agent.agent-version}")
    private String agentVersion;

    private static final int POLL_INTERVAL_MS = 10_000;

    @Autowired
    private MetricsStateHolder stateHolder;

    @Autowired
    private AgentConfig agentContext;

    @Autowired
    private MetricsServiceClient metricsServiceClient;

    @Autowired
    private BackingDeviceCollector backingDeviceCollector;

    @Autowired
    private BlockDeviceCollector blockDeviceCollector;

    /**
     * Monotonically increasing — backend detects gaps in sequence.
     */
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    // ── Report every 30s ──────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    public void reportMetrics() {
        try {
            MetricsAgentResponse payload = assemblePayload();
            metricsServiceClient.send(payload);
        } catch (Exception e) {
            // Never let reporter crash kill the agent process
            log.error("Failed to assemble or send metrics report", e);
        }
    }

    // ── Payload assembly ──────────────────────────────────────────────────────

    private MetricsAgentResponse assemblePayload() {
        List<MountDetail> mounts = assembleMountDetails();
        List<UnmountedDevice> unmountedDevices = assembleUnmountedDevices();
        VmMetrics vmMetrics = buildVmLevelMetrics(mounts, unmountedDevices);

        boolean surgeTriggered = mounts.stream()
                .flatMap(m -> m.getBackingDevices().stream())
                // Only check the logical (first) device — avoids double-counting RAID members
                .filter(d -> d.getDeviceType() != DeviceType.SIMPLE
                        || mounts.stream().anyMatch(m -> m.getBackingDevices().get(0) == d))
                .anyMatch(d -> d.getSurgeMetrics() != null && d.getSurgeMetrics().isSurge());

        return MetricsAgentResponse.builder()
                .clientId(agentContext.getClientId())
                .agentId(agentContext.getAgentId())
                .sentAt(System.currentTimeMillis())
                .sequenceNumber(sequenceNumber.getAndIncrement())
                .pollIntervalMs(POLL_INTERVAL_MS)
                .surgeTriggered(surgeTriggered)
                .vmLevelMetrics(vmMetrics)
                .build();
    }

    // ── Mount assembly ────────────────────────────────────────────────────────

    /**
     * For each configured mount point:
     * 1. Pull capacity + inodes from state holder (written every 60s)
     * 2. Resolve backing devices via BackingDeviceCollector (/proc/mounts + /sys/block)
     * 3. Attach IO + surge per device from state holder (written every 10s)
     */
    private List<MountDetail> assembleMountDetails() {
        Map<String, MountCapacity> capacityMap = stateHolder.latestCapacity();
        Map<String, InodeMetrics> inodeMap = stateHolder.latestInodes();
        Map<String, IoMetrics> ioMap = stateHolder.latestIo();
        Map<String, SurgeMetrics> surgeMap = stateHolder.latestSurge();

        return getMountPoints().stream()
                .map(mountPoint -> {
                    MountCapacity capacity = capacityMap.getOrDefault(mountPoint,
                            MountCapacity.builder().build());
                    InodeMetrics inodes = inodeMap.getOrDefault(mountPoint,
                            InodeMetrics.builder().build());

                    List<BackingDevice> backingDevices =
                            resolveAndEnrichBackingDevices(mountPoint, ioMap, surgeMap);

                    boolean readOnly = isReadOnly(mountPoint);
                    String fsType = resolveFsType(mountPoint);

                    boolean ephemeral = isEphemeral(mountPoint, backingDevices);


                    return MountDetail.builder()
                            .mountPoint(mountPoint)
                            .fsType(fsType)
                            .readOnly(readOnly)
                            .mountCapacity(capacity)
                            .inodes(inodes)
                            .backingDevices(backingDevices)
                            .collectedAt(System.currentTimeMillis())
                            .ephemeral(ephemeral)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Resolves backing devices for a mount and enriches each with
     * IO and surge metrics from the state holder.
     */
    private List<BackingDevice> resolveAndEnrichBackingDevices(
            String mountPoint,
            Map<String, IoMetrics> ioMap,
            Map<String, SurgeMetrics> surgeMap) {
        try {
            List<BackingDevice> devices = backingDeviceCollector.resolveBackingDevices(mountPoint);

            return devices.stream()
                    .map(device -> BackingDevice.builder()
                            .deviceName(device.getDeviceName())
                            .deviceType(device.getDeviceType())
                            // IO and surge may be null on first tick — callers must null-check
                            .ioMetrics(ioMap.get(device.getDeviceName()))
                            .surgeMetrics(surgeMap.get(device.getDeviceName()))
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Could not resolve backing devices for mount={} — skipping IO enrichment", mountPoint, e);
            return Collections.emptyList();
        }
    }

    // ── Unmounted device assembly ─────────────────────────────────────────────

    private List<UnmountedDevice> assembleUnmountedDevices() {
        try {
            return blockDeviceCollector.collectUnmounted();
        } catch (Exception e) {
            log.warn("Could not collect unmounted devices", e);
            return Collections.emptyList();
        }
    }

    // ── VM-level aggregate computation ────────────────────────────────────────

    /**
     * Aggregates are computed from the assembled mount list — not re-read
     * from state — so they are always consistent with the payload.
     * <p>
     * IO aggregation rule: sum only the FIRST (logical) backing device
     * per mount to avoid double-counting physical RAID/LVM members.
     */
    private VmMetrics buildVmLevelMetrics(List<MountDetail> mounts, List<UnmountedDevice> unmountedDevices) {

        long totalReadIops = 0, totalWriteIops = 0;
        long totalReadBytes = 0, totalWriteBytes = 0;
        long totalUsed = 0, totalAvailable = 0;
        int readOnlyCount = 0, criticalInodeCount = 0, surgingCount = 0;

        for (MountDetail mount : mounts) {
            // Capacity aggregates — always available
            if (mount.getMountCapacity() != null) {
                totalUsed += mount.getMountCapacity().getUsedBytes();
                totalAvailable += mount.getMountCapacity().getAvailableBytes();
            }
            if (mount.getInodes() != null && mount.getInodes().getInodeUsagePct() >= 85.0)
                criticalInodeCount++;
            if (mount.isReadOnly())
                readOnlyCount++;

            // IO aggregates — use ONLY the logical (first) device
            if (!mount.getBackingDevices().isEmpty()) {
                BackingDevice logical = mount.getBackingDevices().get(0);

                if (logical.getIoMetrics() != null) {
                    totalReadIops += logical.getIoMetrics().getReadIops();
                    totalWriteIops += logical.getIoMetrics().getWriteIops();
                    totalReadBytes += logical.getIoMetrics().getReadThroughputBytesPerSec();
                    totalWriteBytes += logical.getIoMetrics().getWriteThroughputBytesPerSec();
                }
                if (logical.getSurgeMetrics() != null && logical.getSurgeMetrics().isSurge())
                    surgingCount++;
            }
        }

        int unformatted = (int) unmountedDevices.stream()
                .filter(UnmountedDevice::isUnformatted).count();

        return VmMetrics.builder()
                .vmId(agentContext.getVmId())
                .hostname(resolveHostname())
                .collectedAt(System.currentTimeMillis())
                .agentVersion(agentVersion)
                .mounts(mounts)
                .unmountedDevices(unmountedDevices)
                .totalReadIops(totalReadIops)
                .totalWriteIops(totalWriteIops)
                .totalReadThroughputBytesPerSec(totalReadBytes)
                .totalWriteThroughputBytesPerSec(totalWriteBytes)
                .totalUsedBytes(totalUsed)
                .totalAvailableBytes(totalAvailable)
                .totalMounts(mounts.size())
                .readOnlyMountCount(readOnlyCount)
                .criticalInodeMountCount(criticalInodeCount)
                .surgingMountCount(surgingCount)
                .unmountedDeviceCount(unmountedDevices.size())
                .unformattedDeviceCount(unformatted)
                .build();
    }

    // ── /proc/mounts helpers ──────────────────────────────────────────────────

    /**
     * Reads the mount options field from /proc/mounts.
     * "ro" anywhere in the options means the mount is read-only.
     */
    private boolean isReadOnly(String mountPoint) {
        try {
            return java.nio.file.Files.lines(java.nio.file.Paths.get("/proc/mounts"))
                    .map(line -> line.split("\\s+"))
                    .filter(p -> p.length >= 4 && p[1].equals(mountPoint))
                    .anyMatch(p -> p[3].startsWith("ro"));
        } catch (Exception e) {
            log.warn("Could not determine readOnly status for mount={}", mountPoint);
            return false;
        }
    }

    /**
     * Reads the filesystem type field (column 3) from /proc/mounts.
     * Example line: /dev/sda1 /data ext4 rw,relatime 0 0
     */
    private String resolveFsType(String mountPoint) {
        try {
            return java.nio.file.Files.lines(java.nio.file.Paths.get("/proc/mounts"))
                    .map(line -> line.split("\\s+"))
                    .filter(p -> p.length >= 3 && p[1].equals(mountPoint))
                    .map(p -> p[2])
                    .findFirst()
                    .orElse("unknown");
        } catch (Exception e) {
            log.warn("Could not determine fsType for mount={}", mountPoint);
            return "unknown";
        }
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public List<String> getMountPoints() {
        log.info("................................mounted devices = {} :",agentContext.getMonitor().getMountPoints().toString());
        return  agentContext.getMonitor().getMountPoints();
    }


    // In MetricsReporter.assembleMountDetails()
    private boolean isEphemeral(String mountPoint, List<BackingDevice> backingDevices) {
        // Method 1: Check Azure symlink directly
        Path azureResource = Paths.get("/dev/disk/azure/resource");
        if (Files.exists(azureResource)) {
            try {
                Path target = Files.readSymbolicLink(azureResource);
                String resourceDevice = target.getFileName().toString(); // "sdb"
                boolean backedByResource = backingDevices.stream()
                        .anyMatch(d -> d.getDeviceName().equals(resourceDevice)
                                || d.getDeviceName().contains("resource"));
                if (backedByResource) return true;
            } catch (IOException ignored) {}
        }

        // Method 2: Known Azure temp disk mount points
        return mountPoint.equals("/mnt/resource") || mountPoint.equals("/mnt");
    }
}