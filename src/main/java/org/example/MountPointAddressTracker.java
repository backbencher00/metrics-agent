package org.example;

public class MountPointAddressTracker {
    // Metric Fields
    private String mountPoint;
    private String totalSize;
    private String used;
    private String available;
    private String usagePercentage;

    public MountPointAddressTracker() {}

    // Constructor updated to include identifiers
    public MountPointAddressTracker( String mountPoint,
                                    String totalSize, String used, String available, String usagePercentage) {

        this.mountPoint = mountPoint;
        this.totalSize = totalSize;
        this.used = used;
        this.available = available;
        this.usagePercentage = usagePercentage;
    }

    public String toJson() {
        return "{"
                + "\"mountPoint\":\"" + escape(mountPoint) + "\","
                + "\"totalSize\":\"" + escape(totalSize) + "\","   // FIXED
                + "\"used\":\"" + escape(used) + "\","
                + "\"available\":\"" + escape(available) + "\","
                + "\"usagePercentage\":\"" + escape(usagePercentage) + "\"" // FIXED
                + "}";
    }

    private String escape(String value) {
        return (value == null) ? "" : value.replace("\"", "\\\"");
    }
}