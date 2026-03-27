package com.example.batteryjsontest;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private TextView txtOut;
    private Handler uiHandler;

    // UI refresh every 5 seconds
    private static final long UI_REFRESH_MS = 5_000L;

    private static final String FILE_BATTERY_JSON = "battery_status.json";
    private static final String FILE_LAST_STATUS  = "last_upload_status.txt";

    private String prettyJson(String raw) {
        try {
            raw = raw.trim();
            if (raw.startsWith("{")) {
                return new JSONObject(raw).toString(2);
            } else if (raw.startsWith("[")) {
                return new JSONArray(raw).toString(2);
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private final Runnable uiTask = new Runnable() {
        @Override
        public void run() {
            String rawJson = readTextFromInternalFile(FILE_BATTERY_JSON);
            String lastStatus = readTextFromInternalFile(FILE_LAST_STATUS);

            StringBuilder sb = new StringBuilder();

            sb.append("=== Battery JSON (local) ===\n");
            if (rawJson.isEmpty()) {
                sb.append("Waiting for ").append(FILE_BATTERY_JSON).append("...\n(Start service first)\n");
            } else {
                sb.append(prettyJson(rawJson)).append("\n");
            }

            sb.append("\n=== Last Upload Status (SOAP) ===\n");
            if (lastStatus.isEmpty()) {
                sb.append("No upload status yet.\n");
            } else {
                sb.append(lastStatus).append("\n");
            }

            txtOut.setText(sb.toString());

            uiHandler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtOut = findViewById(R.id.txtOut);
        uiHandler = new Handler(Looper.getMainLooper());

        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop  = findViewById(R.id.btnStop);

        btnStart.setOnClickListener(v -> {
            Intent i = new Intent(this, BatteryMonitorService.class);
            ContextCompat.startForegroundService(this, i);
            txtOut.setText("Service starting... (check notification shade)\n");
        });

        btnStop.setOnClickListener(v -> {
            Intent i = new Intent(this, BatteryMonitorService.class);
            stopService(i);
            txtOut.setText("Service stopped.\n");
        });

        uiHandler.post(uiTask);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(uiTask);
        super.onDestroy();
    }

    private String readTextFromInternalFile(String filename) {
        try (FileInputStream in = openFileInput(filename);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();

        } catch (Exception e) {
            return "";
        }
    }
}
