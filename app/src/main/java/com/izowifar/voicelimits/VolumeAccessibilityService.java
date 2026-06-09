package com.izowifar.voicelimits;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeAccessibilityService extends AccessibilityService {
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable clampRunnable = new Runnable() {
        @Override
        public void run() {
            clampNow();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        clampNow();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (VolumeLimiter.isEnabled(this)) {
            handler.removeCallbacks(clampRunnable);
            handler.postDelayed(clampRunnable, 30L);
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!VolumeLimiter.isEnabled(this)) return false;
        ensureAudioManager();
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            boolean shouldBlock = shouldBlockVolumeUp();
            clampNowSoon();
            return shouldBlock;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            clampNowSoon();
            return false;
        }

        return false;
    }

    private boolean shouldBlockVolumeUp() {
        if (audioManager == null) return false;
        if (!VolumeLimiter.isHeadsetActive(audioManager)) return false;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(audioManager);
        if (current >= limit) {
            VolumeLimiter.clampIfNeeded(audioManager);
            return true;
        }
        return false;
    }

    private void clampNowSoon() {
        clampNow();
        handler.removeCallbacks(clampRunnable);
        handler.postDelayed(clampRunnable, 5L);
        handler.postDelayed(clampRunnable, 15L);
        handler.postDelayed(clampRunnable, 35L);
        handler.postDelayed(clampRunnable, 80L);
        handler.postDelayed(clampRunnable, 150L);
    }

    private void clampNow() {
        ensureAudioManager();
        if (audioManager != null && VolumeLimiter.isEnabled(this) && VolumeLimiter.isHeadsetActive(audioManager)) {
            VolumeLimiter.clampIfNeeded(audioManager);
        }
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
    }
}
