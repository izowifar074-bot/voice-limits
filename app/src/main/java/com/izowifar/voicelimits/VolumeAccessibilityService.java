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
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
                clampNow();
                handler.removeCallbacks(clampRunnable);
                handler.postDelayed(clampRunnable, 10L);
                handler.postDelayed(clampRunnable, 30L);
                handler.postDelayed(clampRunnable, 70L);
            }
        }
        return false;
    }

    private void clampNow() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
        if (audioManager != null && VolumeLimiter.isEnabled(this) && VolumeLimiter.isHeadsetActive(audioManager)) {
            VolumeLimiter.clampIfNeeded(audioManager);
        }
    }
}
