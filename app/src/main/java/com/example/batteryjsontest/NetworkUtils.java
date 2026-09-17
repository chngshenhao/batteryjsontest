package com.example.batteryjsontest;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public final class NetworkUtils {

    private NetworkUtils() {}

    /** True when the active network is Wi‑Fi and has internet capability. */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
