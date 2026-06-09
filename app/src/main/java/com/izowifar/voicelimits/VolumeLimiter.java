package com.izowifar.voicelimits;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

final class VolumeLimiter {
    static final String PREFS = "voice_limits_prefs";
    static final String KEY_ENABLED = "enabled";
    static final int LIMIT_PERCENT = 47;

    private VolumeLimiter() {}

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static boolean isHeadsetActive(AudioManager audioManager) {
        if (audioManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (isHeadsetType(device.getType())) return true;
            }
            return false;
        }
        return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn();
    }

    private static boolean isHeadsetType(int type) {
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_HEARING_AID) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    || type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
        }
        return false;
    }

    static int getLimitVolume(AudioManager audioManager) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.max(1, (int) Math.floor(max * LIMIT_PERCENT / 100.0));
    }

    static boolean clampIfNeeded(AudioManager audioManager) {
        if (audioManager == null) return false;
        int limit = getLimitVolume(audioManager);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (current > limit) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, limit, 0);
            return true;
        }
        return false;
    }
}
