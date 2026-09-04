package com.example.batteryjsontest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryMonitorService extends Service {

    private static final String TAG = "BMS";

    private static final String CHANNEL_ID = "battery_monitor_channel";
    private static final int NOTIF_ID = 1001;

    // update every 1 minute
    private static final long INTERVAL_MS = 60_000L;

    // SOAP ASMX endpoint
    private static final String SOAP_URL =
            "http://bkn1atm03s100/services/automation_smartcabinet.asmx";
    private static final String SOAP_ACTION =
            "http://tempuri.org/put_tab_charging_json";

    // internal files
    private static final String FILE_BATTERY_JSON = "battery_status.json";
    private static final String FILE_LAST_STATUS  = "last_upload_status.txt";

    private Handler handler;
    private ExecutorService netExec;

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler(Looper.getMainLooper());
        netExec = Executors.newSingleThreadExecutor();

        createNotificationChannel();

        Notification notif = buildNotification("Starting...");
        ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );

        handler.post(loopTask); // start immediately
        Log.d(TAG, "Service created. Interval=" + INTERVAL_MS + "ms");
    }

    private final Runnable loopTask = new Runnable() {
        @Override
        public void run() {
            try {
                BatteryInfo info = readBatteryInfo();
                String json = buildJson(info);

                // keep local JSON for your MainActivity display
                writeTextToInternalFile(FILE_BATTERY_JSON, json);

                // send to SOAP every cycle (async)
                sendJsonToSoapAsync(json);

                updateNotification("Battery " + info.batteryPct + "% | Charging: " + info.isCharging);

            } catch (Exception e) {
                Log.e(TAG, "Loop error: " + e.getMessage(), e);

                // also write error as last status so UI can show it
                String statusText = "Time: " + OffsetDateTime.now() + "\n"
                        + "Result: ERROR in loop\n"
                        + "Message: " + e.getMessage() + "\n";
                writeTextToInternalFile(FILE_LAST_STATUS, statusText);

            } finally {
                handler.postDelayed(this, INTERVAL_MS);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(loopTask);
        if (netExec != null) netExec.shutdownNow();
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Battery + JSON ----------

    private BatteryInfo readBatteryInfo() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);

        int level = -1, scale = -1, status = -1;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        }

        int pct = (level >= 0 && scale > 0) ? (level * 100 / scale) : -1;

        boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);

        return new BatteryInfo(pct, charging);
    }

    private String buildJson(BatteryInfo info) {
        try {
            String deviceNumber = DeviceIdUtil.getOrCreateDeviceNumber(this);

            JSONObject obj = new JSONObject();
            obj.put("deviceNumber", deviceNumber);
            obj.put("batteryPct", info.batteryPct);
            obj.put("isCharging", info.isCharging);
            obj.put("timestamp", OffsetDateTime.now().toString());

            return obj.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ---------- SOAP Send (ASMX) ----------

    private void sendJsonToSoapAsync(String json) {
        netExec.execute(() -> sendJsonToSoap(json));
    }

    private void sendJsonToSoap(String json) {
        HttpURLConnection conn = null;
        String now = OffsetDateTime.now().toString();

        try {
            String envelope = buildSoapEnvelope(json);

            URL url = new URL(SOAP_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", "\"" + SOAP_ACTION + "\"");

            byte[] payload = envelope.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
            ));

            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) resp.append(line);

            String respStr = resp.toString();
            Log.d("SOAP_POST", "HTTP " + code);

            // Save last status for UI (keep it readable; avoid huge text)
            String snippet = respStr.length() > 800 ? respStr.substring(0, 800) + "..." : respStr;

            String statusText =
                    "Time: " + now + "\n" +
                            "Result: " + ((code >= 200 && code < 300) ? "SUCCESS" : "FAILED") + "\n" +
                            "HTTP Code: " + code + "\n" +
                            "Response (snippet):\n" + snippet + "\n";

            writeTextToInternalFile(FILE_LAST_STATUS, statusText);

        } catch (Exception e) {
            Log.e("SOAP_POST", "SOAP send failed: " + e.getMessage(), e);

            String statusText =
                    "Time: " + now + "\n" +
                            "Result: ERROR\n" +
                            "Message: " + e.getMessage() + "\n";

            writeTextToInternalFile(FILE_LAST_STATUS, statusText);

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String buildSoapEnvelope(String json) {
        // CDATA keeps your JSON intact (no XML escaping headaches)
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:tem=\"http://tempuri.org/\">"
                + "<soapenv:Header/>"
                + "<soapenv:Body>"
                + "<tem:put_tab_charging_json>"
                + "<tem:stringjson><![CDATA[" + json + "]]></tem:stringjson>"
                + "</tem:put_tab_charging_json>"
                + "</soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    // ---------- Internal file helpers ----------

    private void writeTextToInternalFile(String filename, String text) {
        try (FileOutputStream out = openFileOutput(filename, Context.MODE_PRIVATE)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Write failed (" + filename + "): " + e.getMessage(), e);
        }
    }

    // ---------- Notification ----------

    private Notification buildNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Battery monitoring active")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(content));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Battery Monitor", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    private static class BatteryInfo {
        final int batteryPct;
        final boolean isCharging;

        BatteryInfo(int pct, boolean charging) {
            this.batteryPct = pct;
            this.isCharging = charging;
        }
    }
}
