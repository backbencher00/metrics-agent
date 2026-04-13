package org.metricsAgent.metricsCollectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.metrics.IoMetrics;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class IoMetricsCollector {

    private static final int SECTOR_SIZE_BYTES = 512;


    /**
     * Previous pattern ".*\\d+" was too aggressive — it skipped real
     * partitions like vdb1, sda1, nvme0n1p1 that ARE the mounted device.
     *
     * New rule: skip only pure virtual devices that never represent real IO:
     *   ram\d+   — ramdisks
     *   loop\d+  — loopback devices
     *   zram\d+  — compressed swap
     *
     * dm-\d+ (LVM/multipath) and md\d+ (RAID) are kept — they appear in
     * /proc/mounts and have real IO that needs tracking.
     * Partitions (vdb1, sda1) are kept — they are what /proc/mounts reports.
     */
    private static final String SKIP_PATTERN = "ram\\d+|loop\\d+|zram\\d+";


    /**
     * Reads /proc/diskstats once — this is a kernel virtual FS read,
     * no actual disk IO occurs. Cost: ~microseconds.
     */
    public Map<String, IoRawSnapshot> collectRawSnapshots() throws IOException {
        Map<String, IoRawSnapshot> snapshots = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get("/proc/diskstats"));

        for (String line : lines) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 14) continue;

            String device = p[2];

            // Skip partitions, ramdisks, loopback, device-mapper volumes
            if (device.matches(SKIP_PATTERN)) continue;

            try {
                long reads          = Long.parseLong(p[3]);
                long sectorsRead    = Long.parseLong(p[5]);
                long readTimeMs     = Long.parseLong(p[6]);
                long writes         = Long.parseLong(p[7]);
                long sectorsWritten = Long.parseLong(p[9]);
                long writeTimeMs    = Long.parseLong(p[10]);
                int  queueDepth     = Integer.parseInt(p[11]);
                long ioTimeMs       = Long.parseLong(p[12]);

                snapshots.put(device, new IoRawSnapshot(
                        reads, sectorsRead, readTimeMs,
                        writes, sectorsWritten, writeTimeMs,
                        queueDepth, ioTimeMs
                ));
            } catch (NumberFormatException e) {
                log.warn("Malformed diskstats line, skipping device={}", device);
            }
        }
        return snapshots;
    }

    /**
     * Computes per-second rates from two snapshots.
     * Guards against counter wrap (kernel reboot / module reload).
     */
    public IoMetrics calculateDeltas(IoRawSnapshot curr, IoRawSnapshot prev, int intervalSec) {
        long deltaReads  = curr.reads  - prev.reads;
        long deltaWrites = curr.writes - prev.writes;

        // Guard: counters only go up unless kernel restarted — skip bad interval
        if (deltaReads < 0 || deltaWrites < 0) {
            log.warn("Counter wrap detected — skipping this IO interval");
            return IoMetrics.builder().build();
        }

        long deltaSectorsRead    = curr.sectorsRead    - prev.sectorsRead;
        long deltaSectorsWritten = curr.sectorsWritten - prev.sectorsWritten;
        long deltaReadTimeMs     = curr.readTimeMs     - prev.readTimeMs;
        long deltaWriteTimeMs    = curr.writeTimeMs    - prev.writeTimeMs;
        long deltaIoTimeMs       = curr.ioTimeMs       - prev.ioTimeMs;
        long intervalMs = (long) intervalSec * 1000;

        // Latency: ms spent / operations completed. Guard zero-division.
        double readLatencyMs  = (deltaReads  > 0) ? (double) deltaReadTimeMs  / deltaReads  : 0.0;
        double writeLatencyMs = (deltaWrites > 0) ? (double) deltaWriteTimeMs / deltaWrites : 0.0;

        // Utilisation: how much of the interval the device was busy
        double ioUtilPct = (intervalMs > 0)
                ? Math.min(100.0, ((double) deltaIoTimeMs / intervalMs) * 100.0)
                : 0.0;

        long readIops  = deltaReads  / intervalSec;
        long writeIops = deltaWrites / intervalSec;

        return IoMetrics.builder()
                .readIops(readIops)
                .writeIops(writeIops)
                .totalIops(readIops + writeIops)
                .readLatencyMs(readLatencyMs)
                .writeLatencyMs(writeLatencyMs)
                .queueDepth(curr.queueDepth)           // instantaneous, not a delta
                .ioUtilisationPct(ioUtilPct)
                .readThroughputBytesPerSec(deltaSectorsRead    * SECTOR_SIZE_BYTES / intervalSec)
                .writeThroughputBytesPerSec(deltaSectorsWritten * SECTOR_SIZE_BYTES / intervalSec)
                .build();
    }

    @Data
    @AllArgsConstructor
    public static class IoRawSnapshot {
        long reads, sectorsRead, readTimeMs;
        long writes, sectorsWritten, writeTimeMs;
        int  queueDepth;
        long ioTimeMs;
    }
}