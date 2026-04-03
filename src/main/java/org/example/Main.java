package org.example;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main {
    private static final AtomicBoolean running = new AtomicBoolean(true);

    // ✅ IMPORTANT: Correct endpoint
    private static final String SERVER_URL =
            "https://fourcha-talia-thrasonically.ngrok-free.dev/metrics";

    public static void main(String[] args) {

        System.out.println("🚀 Metric Agent Started...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            System.out.println("🛑 Agent stopped");
        }));

        while (running.get()) {
            try {
                System.out.println("\n⏳ Collecting metrics...");

                DiskMetrics metrics = collectMetrics();

                if (metrics != null) {
                    System.out.println("📊 Metrics: " + metrics);

                    pushToServer(metrics);
                } else {
                    System.out.println("⚠️ No metrics collected");
                }

                Thread.sleep(5000); // 🔥 5 sec for testing

            } catch (Exception e) {
                System.out.println("❌ Error in main loop:");
                e.printStackTrace();
            }
        }
    }

    // ─── Collect Metrics ────────────────────
    private static DiskMetrics collectMetrics() {
        try {

            Process process = Runtime.getRuntime().exec(
                    new String[]{"bash", "-c",
                            "df -h --output=size,used,avail,pcent /storage/data | tail -n 1"}
            );

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line = reader.readLine();
            process.waitFor();

            System.out.println("📥 Raw command output: " + line);

            if (line == null || line.isEmpty()) return null;

            String[] parts = line.trim().split("\\s+");

            if (parts.length < 4) return null;

            return new DiskMetrics(
                    parts[0], // total
                    parts[1], // used
                    parts[2], // available
                    parts[3]  // usage %
            );

        } catch (Exception e) {
            System.out.println("❌ Error collecting metrics:");
            e.printStackTrace();
        }

        return null;
    }

    // ─── Push to Backend ────────────────────
    private static void pushToServer(DiskMetrics metrics) {
        try {
            System.out.println("📡 Sending request to: " + SERVER_URL);

            URL url = new URL(SERVER_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            conn.setConnectTimeout(10000); // 10 sec
            conn.setReadTimeout(10000);

            System.out.println(metrics);
            System.out.println("📦 Payload: " + toJson(metrics));

            int responseCode = conn.getResponseCode();
            System.out.println("📡 Response Code: " + responseCode);

            BufferedReader br;

            if (responseCode >= 200 && responseCode < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("📨 Response Body: " + line);
            }

        } catch (Exception e) {
            System.out.println("❌ Failed to push metrics:");
            e.printStackTrace();
        }
    }

    public static String toJson(DiskMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"total\":\"").append(escape(metrics.getTotalStorage())).append("\",");
        sb.append("\"used\":\"").append(escape(metrics.getUsed())).append("\",");
        sb.append("\"available\":\"").append(escape(metrics.getUsed())).append("\",");
        sb.append("\"usage\":\"").append(escape(metrics.getUsagePercentage())).append("\"");
        sb.append("}");
        return sb.toString();
    }
    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}