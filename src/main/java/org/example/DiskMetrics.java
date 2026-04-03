package org.example;

public class DiskMetrics {


    private String totalSize;

    private String used;
    private String available;

    private String usagePercentage;

    // Default constructor (important for Jackson)
    public DiskMetrics() {
    }

    // Parameterized constructor
    public DiskMetrics(String totalSize, String used, String available, String usagePercentage) {
        this.totalSize = totalSize;
        this.used = used;
        this.available = available;
        this.usagePercentage = usagePercentage;
    }

    // Getters
    public String getTotalStorage() {
        return totalSize;
    }

    public String getUsed() {
        return used;
    }

    public String getAvailable() {
        return available;
    }

    public String getUsagePercentage() {
        return usagePercentage;
    }

    // Setters
    public void setTotalSize(String totalSize) {
        this.totalSize = totalSize;
    }

    public void setUsed(String used) {
        this.used = used;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    public void setUsagePercentage(String usagePercentage) {
        this.usagePercentage = usagePercentage;
    }

    // toString method
    @Override
    public String toString() {
        return "DiskMetrics{" +
                "totalSize='" + totalSize + '\'' +
                ", used='" + used + '\'' +
                ", available='" + available + '\'' +
                ", usagePercentage='" + usagePercentage + '\'' +
                '}';
    }
}