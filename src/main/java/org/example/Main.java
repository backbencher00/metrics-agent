package org.example;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static final String SERVER_URL = "http://localhost:3117/metrics";

    // This URL tunnels to your localhost:3117 via ngrok
    public static void main(String[] args) {
        MetricsService service = new MetricsService();
        System.out.println("🚀 Agent started. Tracking metrics for lvm-lab...");

        // Shutdown hook to handle Ctrl+C gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Stopping agent...");
            running.set(false);
        }));

        while (running.get()) {
            try {
                // 1. Get metrics (Metadata is already added inside the Service)
                VmNodeMetrics trackedMetrics = service.getTrackedMetrics();

                if (trackedMetrics== null && trackedMetrics.getMountPointMetrics()== null && trackedMetrics.getMountPointMetrics().isEmpty()) {
                    System.out.println("⚠️ No matching mount points found in df output.");
                }
                MetricsHttpClient client = new MetricsHttpClient(SERVER_URL);
                client.pushMetrics(trackedMetrics);
                // 3. Wait 1 minute before next scan
                TimeUnit.SECONDS.sleep(15);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("❌ Error in main loop: " + e.getMessage());
            }
        }
    }
}