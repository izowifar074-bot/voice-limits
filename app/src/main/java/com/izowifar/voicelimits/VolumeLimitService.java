package com.izowifar.voicelimits;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class VolumeLimitService extends Service {
    static final String ACTION_START = "com.izowifar.voicelimits.START";
    static final String ACTION_STOP = "com.izowifar.voicelimits.STOP";
    private static final String CHANNEL_ID = "voice_limits_running";
    private static final int NOTIFICATION_ID = 4701;
    private static final long CHECK_INTERVAL_MS = 650L;

    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioDeviceCallback deviceCallback;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!VolumeLimiter.isEnabled(VolumeLimitService.this)) {
                stopSelf();
                return;
            }
            enforceLimit();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("正在保护耳机音量：上限 47%"));
        registerDeviceCallback();
        handler.post(checkRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            VolumeLimiter.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        VolumeLimiter.setEnabled(this, true);
        enforceLimit();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null && deviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
        }
        super.onDestroy();
    }

    private void registerDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            deviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    enforceLimit();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    enforceLimit();
                }
            };
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }
    }

    private void enforceLimit() {
        if (audioManager == null) return;
        if (VolumeLimiter.isHeadsetActive(audioManager)) {
            boolean changed = VolumeLimiter.clampIfNeeded(audioManager);
            if (changed) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, buildNotification("已把耳机媒体音量压到 47% 以下"));
                }
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Voice Limits 已启用")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Limits 后台服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持耳机媒体音量低于 47%");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
