package org.metricsAgent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.MountCapacity;
import org.metricsAgent.config.AgentConfig;
import org.metricsAgent.metrics.InodeMetrics;
import org.metricsAgent.metrics.IoMetrics;
import org.metricsAgent.metrics.SurgeMetrics;
import org.metricsAgent.metricsCollectors.IoMetricsCollector;
import org.metricsAgent.metricsCollectors.MountCapacityCollector;
import org.metricsAgent.metricsCollectors.SurgeMetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsOrchestrator {

    private static final int INTERVAL_SEC = 2;  // IO delta interval — must match fixedDelay below

    @Autowired
    AgentConfig agentConfig;

    private final MountCapacityCollector capacityCollector;
    private final IoMetricsCollector     ioCollector;
    private final MetricsStateHolder     stateHolder;

    /**
     * One SurgeMetricsCollector per DEVICE (not per mount).
     * Each instance owns its own 60s rolling window — must survive
     * across ticks so the baseline builds up correctly.
     * Created lazily on first IO tick for each device.
     */
    private final Map<String, SurgeMetricsCollector> surgeCollectors = new ConcurrentHashMap<>();

    /**
     * Previous /proc/diskstats snapshots — one entry per device.
     * AtomicReference ensures the IO tick sees a consistent previous state
     * even if the reference is swapped mid-read.
     */
    private final AtomicReference<Map<String, IoMetricsCollector.IoRawSnapshot>> previousSnapshots
            = new AtomicReference<>(new ConcurrentHashMap<>());

    // ── Capacity + Inodes — slow path, every 60s ──────────────────────────────
    // statfs() is cheap but 60s is fine — disk fills slowly
    @Scheduled(fixedDelay = 60_000)
    public void collectMountCapacityMetrics() {
        for (String mount : agentConfig.getMonitor().getMountPoints()) {

            // ── Capacity — must succeed independently of inode collection ──────────
            try {
                MountCapacity capacity = capacityCollector.collectCapacity(mount);
                stateHolder.updateCapacity(mount, capacity);

                if (capacity.getUsagePct() >= 85.0)
                    log.warn("DISK HIGH: mount={} usagePct={}", mount,
                            String.format("%.1f", capacity.getUsagePct()));

            } catch (Exception e) {
                log.error("Capacity collection failed mount={}", mount, e);
            }

            // ── Inodes — non-fatal, btrfs/tmpfs legitimately don't support this ──
            try {
                InodeMetrics inodes = capacityCollector.collectInodes(mount);
                stateHolder.updateInodes(mount, inodes);

                if (inodes.getTotalInodes() > 0 && inodes.getInodeUsagePct() >= 85.0)
                    log.warn("INODE HIGH: mount={} usagePct={}", mount,
                            String.format("%.1f", inodes.getInodeUsagePct()));

            } catch (UnsupportedOperationException e) {
                // btrfs and tmpfs don't expose inode counts via statvfs — this is expected
                log.debug("Inode tracking unsupported for mount={} (btrfs/tmpfs/virtual)", mount);
                stateHolder.updateInodes(mount, InodeMetrics.builder().build());
            } catch (Exception e) {
                log.warn("Inode collection failed mount={}", mount, e);
                stateHolder.updateInodes(mount, InodeMetrics.builder().build());
            }
        }
    }

    // ── IO + Surge — fast path, every 10s ────────────────────────────────────
    //
    // IMPORTANT: surge is computed HERE in the same tick as IO.
    // Both need the (curr, prev) snapshot pair — reading diskstats twice
    // would introduce a race and waste a syscall. One read, one tick.
    //
    // Covers ALL devices in /proc/diskstats — mounted and unmounted.
    // The reporter decides what to do with unmounted device IO (e.g. raw block access).
    @Scheduled(fixedDelay = 2_000)  // Sample IO every 2s — faster than the 10s reporting interval
    public void collectIoAndSurgeMetrics() {
        try {
            Map<String, IoMetricsCollector.IoRawSnapshot> current  = ioCollector.collectRawSnapshots();
            Map<String, IoMetricsCollector.IoRawSnapshot> previous = previousSnapshots.get();

            current.forEach((device, currSnapshot) -> {
                IoMetricsCollector.IoRawSnapshot prevSnapshot = previous.get(device);

                // First tick for this device — no previous snapshot, skip delta
                if (prevSnapshot == null) return;

                IoMetrics io = ioCollector.calculateDeltas(currSnapshot, prevSnapshot, INTERVAL_SEC);

                // SurgeMetricsCollector is stateful — must reuse same instance per device
                SurgeMetrics surge = surgeCollectors
                        .computeIfAbsent(device, d -> new SurgeMetricsCollector())
                        .compute(currSnapshot, prevSnapshot);

                stateHolder.updateIo(device, io);
                stateHolder.updateSurge(device, surge);
            });

            // Atomic swap — the reporter's read of previousSnapshots is never partial
            previousSnapshots.set(current);

        } catch (IOException e) {
            log.error("IO collection failed — /proc/diskstats unreadable", e);
        }
    }
}