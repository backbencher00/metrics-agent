package org.metricsAgent.service;

import lombok.extern.slf4j.Slf4j;
import org.metricsAgent.LVMstoragePool.MountCapacity;
import org.metricsAgent.metrics.*;
import org.metricsAgent.response.VmMetrics;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-process state bus.
 *
 * The orchestrator writes here on every collection tick.
 * The reporter reads a consistent snapshot every 30s.
 *
 * AtomicReference per map ensures the reporter never sees a
 * partially-updated device map, even without locks.
 */
@Slf4j
@Component
public class MetricsStateHolder {

    // device → latest IoMetrics
    private final AtomicReference<Map<String, IoMetrics>> ioMetrics
            = new AtomicReference<>(new ConcurrentHashMap<>());

    // device → latest SurgeMetrics
    private final AtomicReference<Map<String, SurgeMetrics>> surgeMetrics
            = new AtomicReference<>(new ConcurrentHashMap<>());

    // mount → latest MountLevelMetrics
    private final AtomicReference<Map<String, MountCapacity>> capacityMetrics
            = new AtomicReference<>(new ConcurrentHashMap<>());

    // mount → latest InodeMetrics
    private final AtomicReference<Map<String, InodeMetrics>> inodeMetrics
            = new AtomicReference<>(new ConcurrentHashMap<>());


    // ── Writers (called by orchestrator) ─────────────────────────────────────

    public void updateIo(String device, IoMetrics io) {
        ioMetrics.get().put(device, io);
    }

    public void updateSurge(String device, SurgeMetrics surge) {
        surgeMetrics.get().put(device, surge);
    }

    public void updateCapacity(String mount, MountCapacity cap) {
        capacityMetrics.get().put(mount, cap);
    }

    public void updateInodes(String mount, InodeMetrics inodes) {
        inodeMetrics.get().put(mount, inodes);
    }

    // ── Readers (called by reporter) ──────────────────────────────────────────

    public Map<String, IoMetrics>         latestIo()       { return ioMetrics.get(); }
    public Map<String, SurgeMetrics>      latestSurge()    { return surgeMetrics.get(); }
    public Map<String, MountCapacity> latestCapacity() { return capacityMetrics.get(); }
    public Map<String, InodeMetrics>      latestInodes()   { return inodeMetrics.get(); }
}