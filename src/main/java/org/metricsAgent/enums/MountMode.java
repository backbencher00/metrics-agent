package org.metricsAgent.enums;

/**
 * Mount access mode parsed from /proc/mounts (field 4).
 *
 * /proc/mounts line format:
 *   device  mountPoint  fsType  options  dump  pass
 *
 * options field contains "rw" or "ro" as the first option token.
 *
 * READ_ONLY is a critical silent failure — writes fail without any
 * disk-full warning. Common causes: filesystem errors triggering kernel
 * remount-ro, or misconfigured fstab.
 */
public enum MountMode {
    READ_WRITE,   // "rw" — normal
    READ_ONLY     // "ro" — CRITICAL: triggers expand/repair alert immediately
}