package com.izowifar.voicelimits;

import android.app.AlarmManager;
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
import android.os.SystemClock;

public class VolumeLimitService extends Service {
    static final String ACTION_START = "com.izowifar.voicelimits.START";
    static final String ACTION_STOP = "com.izowifar.voicelimits.STOP";
    private static final String CHANNEL_ID = "voice_limits_running";
    private static final int NOTIFICATION_ID = 4701;
    private static final int KEEP_ALIVE_REQUEST_CODE = 4702;

    // Deliberately low-frequency. The AccessibilityService handles the volume key path.
    // This service is only a visible status + rare safeguard, avoiding volume UI oscillation.
    private static final long SAFE_CHECK_INTERVAL_MS = 10_000L;
    private static final long KEEP_ALIVE_INTERVAL_MS = 60_000L;

    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioDeviceCallback deviceCallback;
    private long lastCorrectionAt = 0L;

    private final Runnable safeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!VolumeLimiter.isEnabled(VolumeLimitService.this)) {
                cancelKeepAlive(VolumeLimitService.this);
                stopSelf();
                return;
            }
            enforceLimitWithCooldown();
            handler.postDelayed(this, SAFE_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("正在保护耳机音量：上限 47%"));
        registerDeviceCallback();
        handler.post(safeCheckRunnable);
        scheduleKeepAlive(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            VolumeLimiter.setEnabled(this, false);
            cancelKeepAlive(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        VolumeLimiter.setEnabled(this, true);
        scheduleKeepAlive(this);
        enforceLimitWithCooldown();
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
        if (VolumeLimiter.isEnabled(this)) {
            scheduleKeepAlive(this);
        }
        super.onDestroy();
    }

    static void scheduleKeepAlive(Context context) {
        if (!VolumeLimiter.isEnabled(context)) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(KeepAliveReceiver.ACTION_KEEP_ALIVE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                KEEP_ALIVE_REQUEST_CODE,
                intent,
                pendingIntentFlags()
        );

        long triggerAt = SystemClock.elapsedRealtime() + KEEP_ALIVE_INTERVAL_MS;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException ignored) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    static void cancelKeepAlive(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        intent.setAction(KeepAliveReceiver.ACTION_KEEP_ALIVE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                KEEP_ALIVE_REQUEST_CODE,
                intent,
                pendingIntentFlags()
        );
        alarmManager.cancel(pendingIntent);
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void registerDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            deviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    enforceLimitWithCooldown();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    // Do nothing. Avoid fighting system routing changes.
                }
            };
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }
    }

    private void enforceLimitWithCooldown() {
        if (audioManager == null) return;
        if (!VolumeLimiter.isHeadsetActive(audioManager)) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(audioManager);
        long now = SystemClock.elapsedRealtime();
        if (current > limit && now - lastCorrectionAt > 1500L) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, limit, 0);
            lastCorrectionAt = now;
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
