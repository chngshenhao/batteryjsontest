package com.example.batteryjsontest;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

public final class ServiceStarter {

    private static final String TAG = "ServiceStarter";

    private ServiceStarter() {}

    /** Start the battery monitor foreground service if Wi‑Fi is connected. */
    public static void startIfWifiConnected(Context context) {
        if (!NetworkUtils.isWifiConnected(context)) {
            Log.d(TAG, "Skip start: Wi‑Fi not connected");
            return;
        }
        start(context);
    }

    public static void start(Context context) {
        try {
            Intent i = new Intent(context, BatteryMonitorService.class);
            ContextCompat.startForegroundService(context, i);
            Log.d(TAG, "BatteryMonitorService start requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BatteryMonitorService: " + e.getMessage(), e);
        }
    }
}
