package com.example.batteryjsontest;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Resolves deviceNumber from the system "Device name" (Settings → About phone).
 */
public class DeviceIdUtil {

    private static final String TAG = "DeviceIdUtil";

    public static synchronized String getOrCreateDeviceNumber(Context ctx) {
        String name = readSystemDeviceName(ctx);
        if (!TextUtils.isEmpty(name)) {
            return name.trim();
        }

        // Fallback if Device name is unset
        Log.w(TAG, "Device name empty; falling back to Build.MODEL");
        if (!TextUtils.isEmpty(Build.MODEL)) {
            return Build.MODEL.trim();
        }
        return "UNKNOWN";
    }

    private static String readSystemDeviceName(Context ctx) {
        try {
            // Same value shown as "Device name" in About phone (API 25+)
            String global = Settings.Global.getString(
                    ctx.getContentResolver(), Settings.Global.DEVICE_NAME);
            if (!TextUtils.isEmpty(global)) {
                return global;
            }
        } catch (Exception e) {
            Log.w(TAG, "Settings.Global.DEVICE_NAME failed: " + e.getMessage());
        }

        try {
            // Some OEM builds expose the name under Secure
            String secure = Settings.Secure.getString(
                    ctx.getContentResolver(), "bluetooth_name");
            if (!TextUtils.isEmpty(secure)) {
                return secure;
            }
        } catch (Exception e) {
            Log.w(TAG, "bluetooth_name failed: " + e.getMessage());
        }

        return null;
    }
}
