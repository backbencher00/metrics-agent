package org.metricsAgent.response;

import lombok.Builder;
import lombok.Data;

/**
 * Top-level response envelope sent from the metrics agent to the backend service.
 *
 * The agent on each VM collects all metrics, computes surge rates and deltas
 * in-process, then POSTs this object to the backend at each poll interval.
 *
 * Flow:
 *   VM agent  →  POST /api/metrics/ingest  →  Backend service
 *                                              ↓
 *                                         Score calculation
 *                                         Threshold evaluation
 *                                         Expand / shrink decisions
 */
@Data
@Builder
public class MetricsAgentResponse {

    // ── CLIENT / TENANT IDENTITY ──────────────────────────────────────────────

    /**
     * Tenant or customer ID — identifies which account this VM belongs to.
     * Used by backend to route metrics to the correct dashboard / alert config.
     */
    private String clientId;

    /**
     * Unique ID of this agent instance.
     * Format: "{clientId}-{vmId}-agent"
     * Helps detect duplicate or stale agent reports.
     */
    private String agentId;

    // ── ENVELOPE METADATA ────────────────────────────────────────────────────

    /**
     * Epoch ms when the agent sent this payload.
     * Backend should compare with collectedAt inside vmLevelMetrics
     * to detect network delay or clock skew.
     */
    private long sentAt;

    /**
     * Sequential counter incremented by the agent each poll cycle.
     * Backend can detect missed reports by checking for gaps.
     */
    private long sequenceNumber;

    /**
     * Poll interval in milliseconds used by this agent.
     * Typically 1000ms (1s) — needed by backend to interpret
     * per-second IO rates correctly.
     */
    private int pollIntervalMs;

    /**
     * True if this report was triggered by a surge event rather than
     * the normal schedule — allows backend to prioritise processing.
     */
    private boolean surgeTriggered;

    // ── PAYLOAD ───────────────────────────────────────────────────────────────

    /**
     * Full VM-level and mount-level metrics for this collection cycle.
     * Contains: block device inventory, aggregated IO, per-mount detail.
     */
    private VmMetrics vmLevelMetrics;
}