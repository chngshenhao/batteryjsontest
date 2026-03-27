package com.example.batteryjsontest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Locale;
import java.util.UUID;

public class DeviceIdUtil {

    private static final String PREF = "pda_prefs";
    private static final String KEY  = "device_number";

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    public static synchronized String getOrCreateDeviceNumber(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String existing = sp.getString(KEY, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            if (existing != null && !existing.trim().isEmpty()) {
                return existing;
            }
        }

        String newId = "DEV-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.US);

        // commit() ensures it is written immediately (useful during testing)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            sp.edit().putString(KEY, newId).apply();
        }
        return newId;
    }
}
