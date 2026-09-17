package com.example.batteryjsontest;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

/**
 * Keeps a NetworkCallback registered while the process is alive so the
 * battery service starts as soon as Wi‑Fi becomes available.
 */
public class BatteryApp extends Application {

    private static final String TAG = "BatteryApp";

    private final ConnectivityManager.NetworkCallback wifiCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    ServiceStarter.startIfWifiConnected(BatteryApp.this);
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        ServiceStarter.startIfWifiConnected(BatteryApp.this);
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        registerWifiCallback();
        ServiceStarter.startIfWifiConnected(this);
    }

    private void registerWifiCallback() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        try {
            cm.registerNetworkCallback(request, wifiCallback);
            Log.d(TAG, "Wi‑Fi NetworkCallback registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register NetworkCallback: " + e.getMessage(), e);
        }
    }
}
