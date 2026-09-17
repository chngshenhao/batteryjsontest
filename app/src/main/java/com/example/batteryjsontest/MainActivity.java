package com.example.batteryjsontest;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ADMIN_PASSWORD = "Automation";
    private static final long ADMIN_IDLE_MS = 5 * 60_000L;
    private static final long UI_REFRESH_MS = 5_000L;

    private static final String FILE_BATTERY_JSON = "battery_status.json";
    private static final String FILE_LAST_STATUS = "last_upload_status.txt";

    private TextView txtOut;
    private TextView txtDeviceNumber;
    private TextView txtServiceStatus;
    private TextView txtReturnHint;
    private ImageView imgQr;
    private Button btnStart;
    private Button btnCheckUpdate;
    private ScrollView scrollDebug;
    private Handler uiHandler;

    private boolean adminMode = false;

    private final Runnable adminIdleLogout = () -> {
        if (!adminMode) return;
        adminMode = false;
        applyModeUi();
        Toast.makeText(this, R.string.admin_logout, Toast.LENGTH_SHORT).show();
    };

    private final Runnable uiTask = new Runnable() {
        @Override
        public void run() {
            updateServiceStatus();
            if (adminMode) {
                updateDebugPanel();
            }
            uiHandler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtOut = findViewById(R.id.txtOut);
        txtDeviceNumber = findViewById(R.id.txtDeviceNumber);
        txtServiceStatus = findViewById(R.id.txtServiceStatus);
        txtReturnHint = findViewById(R.id.txtReturnHint);
        imgQr = findViewById(R.id.imgQr);
        btnStart = findViewById(R.id.btnStart);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        scrollDebug = findViewById(R.id.scrollDebug);
        ImageButton btnDebug = findViewById(R.id.btnDebug);
        uiHandler = new Handler(Looper.getMainLooper());

        txtReturnHint.setText(
                getString(R.string.return_hint_en) + "\n\n" + getString(R.string.return_hint_ms)
        );

        btnDebug.setOnClickListener(v -> {
            resetAdminIdleTimer();
            if (adminMode) {
                exitAdminMode();
            } else {
                promptAdminPassword();
            }
        });

        btnStart.setOnClickListener(v -> {
            resetAdminIdleTimer();
            ServiceStarter.start(this);
            Toast.makeText(this, "Service starting…", Toast.LENGTH_SHORT).show();
            updateServiceStatus();
        });

        btnCheckUpdate.setOnClickListener(v -> {
            resetAdminIdleTimer();
            AppUpdateChecker.check(this, true);
        });

        showDeviceQr();
        applyModeUi();
        updateServiceStatus();

        ServiceStarter.startIfWifiConnected(this);
        AppUpdateChecker.ensureInstallPermission(this);
        AppUpdateChecker.check(this);

        uiHandler.post(uiTask);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (adminMode && ev.getAction() == MotionEvent.ACTION_DOWN) {
            resetAdminIdleTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void promptAdminPassword() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        input.setHint(R.string.admin_password_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_password_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String entered = input.getText() != null ? input.getText().toString() : "";
                    if (ADMIN_PASSWORD.equals(entered)) {
                        enterAdminMode();
                    } else {
                        Toast.makeText(this, R.string.admin_password_wrong, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void enterAdminMode() {
        adminMode = true;
        applyModeUi();
        updateDebugPanel();
        resetAdminIdleTimer();
    }

    private void exitAdminMode() {
        if (!adminMode) return;
        adminMode = false;
        uiHandler.removeCallbacks(adminIdleLogout);
        applyModeUi();
    }

    private void resetAdminIdleTimer() {
        if (!adminMode) return;
        uiHandler.removeCallbacks(adminIdleLogout);
        uiHandler.postDelayed(adminIdleLogout, ADMIN_IDLE_MS);
    }

    private void applyModeUi() {
        int adminVisibility = adminMode ? View.VISIBLE : View.GONE;
        btnStart.setVisibility(adminVisibility);
        btnCheckUpdate.setVisibility(adminVisibility);
        scrollDebug.setVisibility(adminVisibility);
    }

    private void showDeviceQr() {
        String deviceNumber = DeviceIdUtil.getOrCreateDeviceNumber(this);
        txtDeviceNumber.setText(getString(R.string.device_label, deviceNumber));

        try {
            int sizePx = (int) (240 * getResources().getDisplayMetrics().density);
            Bitmap qr = QrCodeUtil.encode(deviceNumber, sizePx);
            imgQr.setImageBitmap(qr);
            imgQr.setContentDescription("QR: " + deviceNumber);
        } catch (Exception e) {
            Log.e(TAG, "QR generate failed: " + e.getMessage(), e);
            txtDeviceNumber.setText(getString(R.string.device_label, deviceNumber) + "\n(QR generate failed)");
        }
    }

    private void updateServiceStatus() {
        boolean online = BatteryMonitorService.isRunning();
        if (online) {
            txtServiceStatus.setText(R.string.service_online);
            txtServiceStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            txtServiceStatus.setText(R.string.service_offline);
            txtServiceStatus.setTextColor(Color.parseColor("#C62828"));
        }
    }

    private void updateDebugPanel() {
        String rawJson = readTextFromInternalFile(FILE_BATTERY_JSON);
        String lastStatus = readTextFromInternalFile(FILE_LAST_STATUS);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Battery JSON (local) ===\n");
        if (rawJson.isEmpty()) {
            sb.append("Waiting for ").append(FILE_BATTERY_JSON)
                    .append("...\n(Service starts automatically when Wi‑Fi is connected)\n");
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
    }

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

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(uiTask);
        uiHandler.removeCallbacks(adminIdleLogout);
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
