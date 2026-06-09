package com.izowifar.voicelimits;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;

public class KeepAliveReceiver extends BroadcastReceiver {
    static final String ACTION_KEEP_ALIVE = "com.izowifar.voicelimits.KEEP_ALIVE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!VolumeLimiter.isEnabled(context)) return;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null && VolumeLimiter.isHeadsetActive(audioManager)) {
            VolumeLimiter.clampIfNeeded(context, audioManager);
        }

        Intent serviceIntent = new Intent(context, VolumeLimitService.class);
        serviceIntent.setAction(VolumeLimitService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }

        VolumeLimitService.scheduleKeepAlive(context);
    }
}
