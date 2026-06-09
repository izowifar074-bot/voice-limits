package com.izowifar.voicelimits;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!VolumeLimiter.isEnabled(context)) return;
        Intent serviceIntent = new Intent(context, VolumeLimitService.class);
        serviceIntent.setAction(VolumeLimitService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
