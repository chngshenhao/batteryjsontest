package com.example.batteryjsontest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

/**
 * Starts BatteryMonitorService when the device connects to Wi‑Fi,
 * and after boot if Wi‑Fi is already connected.
 */
public class WifiConnectedReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiConnectedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ServiceStarter.startIfWifiConnected(context);
            return;
        }

        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO, NetworkInfo.class);
            } else {
                info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            }
            if (info != null && info.isConnected()) {
                ServiceStarter.startIfWifiConnected(context);
            }
            return;
        }

        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                // Process is now alive; Application NetworkCallback will start
                // the service once Wi‑Fi association completes.
                ServiceStarter.startIfWifiConnected(context);
            }
        }
    }
}
