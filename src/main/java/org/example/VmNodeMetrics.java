package org.example;

import java.util.List;

public class VmNodeMetrics {

    // Metadata Fields
    private String organizationId;
    private String clientId;
    private String vm_id;
    private List<MountPointAddressTracker> mountPointMetrics;

    // Default constructor
    public VmNodeMetrics() {
    }

    // Parameterized constructor
    public VmNodeMetrics(String organizationId, String clientId, String vm_name,
                         List<MountPointAddressTracker> mountPointMetrics) {
        this.organizationId = organizationId;
        this.clientId = clientId;
        this.vm_id = vm_name;
        this.mountPointMetrics = mountPointMetrics;
    }

    // Getters
    public String getOrganizationId() {
        return organizationId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getVmName() {
        return vm_id;
    }

    public List<MountPointAddressTracker> getMountPointMetrics() {
        return mountPointMetrics;
    }

    // Setters
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setVmName(String vm_name) {
        this.vm_id = vm_name;
    }

    public void setMountPointMetrics(List<MountPointAddressTracker> mountPointMetrics) {
        this.mountPointMetrics = mountPointMetrics;
    }

    // toString
    @Override
    public String toString() {
        return "VmNodeMetrics{" +
                "organizationId='" + organizationId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", vm_name='" + vm_id+ '\'' +
                ", mountPointMetrics=" + mountPointMetrics +
                '}';
    }
    public String toJson() {
        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("\"organizationId\":\"").append(escape(organizationId)).append("\",");
        sb.append("\"clientId\":\"").append(escape(clientId)).append("\",");
        sb.append("\"vm_id\":\"").append(escape(vm_id)).append("\",");

        // List of mount points
        sb.append("\"mountPointMetrics\":[");

        if (mountPointMetrics != null) {
            for (int i = 0; i < mountPointMetrics.size(); i++) {
                sb.append(mountPointMetrics.get(i).toJson());
                if (i != mountPointMetrics.size() - 1) {
                    sb.append(",");
                }
            }
        }

        sb.append("]");
        sb.append("}");

        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
