package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetricsService {

    // Your specific tracking list
    private static final List<String> TRACKED_PATHS = Arrays.asList("/mnt/lvm-lab");

    public VmNodeMetrics getTrackedMetrics() {
        List<MountPointAddressTracker> metricsList = new ArrayList<>();

        try {
            // Command gets Target, Size, Used, Avail, Percent (No header)
            String cmd = "df -h --output=target,size,used,avail,pcent | tail -n +2";
            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5) {
                    String currentPath = parts[0];
                    
                    // Only add if it's in our "mount_point_tracking" list
                    if (TRACKED_PATHS.contains(currentPath)) {
                        // Inside your MetricsService loop:
                        metricsList.add(new MountPointAddressTracker(
                                currentPath,     // From df command
                                parts[1],        // From df command
                                parts[2],        // From df command
                                parts[3],        // From df command
                                parts[4]         // From df command
                        ));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return buildVmNodeMetrics(metricsList);
    }

    public String getVmName() {
        try {
            Process process = Runtime.getRuntime().exec("hostname");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim(); // Returns "lvm-lab"
        } catch (Exception e) {
            return "unknown-vm";
        }
    }

    public VmNodeMetrics buildVmNodeMetrics(List<MountPointAddressTracker> pointAddressTrackers){
         String vmNodeName = getVmName();
         VmNodeMetrics vmNodeMetrics = new VmNodeMetrics();
         vmNodeMetrics.setClientId("lvm-lab");
         vmNodeMetrics.setOrganizationId("Kotak");
         vmNodeMetrics.setVmName(vmNodeName);
         vmNodeMetrics.setMountPointMetrics(pointAddressTrackers);
         return vmNodeMetrics;

    }
}