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

    // New channel id so Android creates a visible status notification channel even if an
    // older low-importance channel already exists from previous builds.
    private static final String CHANNEL_ID = "voice_limits_status_v2";
    private static final int NOTIFICATION_ID = 4701;
    private static final int KEEP_ALIVE_REQUEST_CODE = 4702;

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
            updateRunningNotification();
            handler.postDelayed(this, SAFE_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildRunningNotification());
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
        updateRunningNotification();
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
                    updateRunningNotification();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    updateRunningNotification();
                }
            };
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }
    }

    private void enforceLimitWithCooldown() {
        if (audioManager == null) return;
        if (!VolumeLimiter.isHeadsetActive(audioManager)) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(this, audioManager);
        long now = SystemClock.elapsedRealtime();
        if (current > limit && now - lastCorrectionAt > 1500L) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, limit, 0);
            lastCorrectionAt = now;
        }
    }

    private void updateRunningNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildRunningNotification());
        }
    }

    private Notification buildRunningNotification() {
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
                .setContentTitle("Voice Limits 正在运行")
                .setContentText(buildStatusText())
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build();
    }

    private String buildStatusText() {
        if (audioManager == null) return "正在保护耳机音量";
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(this, audioManager);
        boolean headset = VolumeLimiter.isHeadsetActive(audioManager);
        return (headset ? "耳机已连接" : "等待耳机")
                + "｜当前 " + current + "/" + max
                + "｜上限 " + limit + "/" + max;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Limits 运行状态",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("显示 Voice Limits 正在运行和当前耳机音量上限");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
