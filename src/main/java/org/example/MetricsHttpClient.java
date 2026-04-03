package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MetricsHttpClient {

     private final String serverUrl;

    public MetricsHttpClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void pushMetrics(VmNodeMetrics trackedMetrics) {
        try {
            System.out.println("📡 Sending request to: " + serverUrl);
            URL url = new URL(serverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // Set Headers
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Write Body
            String payload = trackedMetrics.toJson();
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("📦 Payload: " + payload);
            System.out.println("📡 Response Code: " + responseCode);

            // Read Response (Success or Error)
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                        (responseCode >= 200 && responseCode < 300) 
                        ? conn.getInputStream() : conn.getErrorStream(), 
                        StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("📨 Response Body: " + line);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to push metrics for " + trackedMetrics.getClientId());
            e.printStackTrace();
        }
    }
}