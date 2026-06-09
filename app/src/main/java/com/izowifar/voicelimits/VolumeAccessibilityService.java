package com.izowifar.voicelimits;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeAccessibilityService extends AccessibilityService {
    private AudioManager audioManager;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally no-op. Volume changes are handled only from key DOWN events.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!VolumeLimiter.isEnabled(this)) return false;

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }

        ensureAudioManager();
        if (audioManager == null) return false;
        if (!VolumeLimiter.isHeadsetActive(audioManager)) return false;

        // Do not handle ACTION_UP. Handling key-up near the limit can confuse some systems
        // and may be related to the runaway-to-max behavior seen on the device.
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        // Global long-press guard: for both volume-up and volume-down, allow only the first
        // DOWN event to reach Android. All repeated DOWN events are consumed.
        // This makes a physical long press behave like one normal tap.
        if (event.getRepeatCount() > 0) {
            return true;
        }

        // Let Android handle normal single taps so the system volume panel still appears.
        // Only block volume-up when it is already at the configured headset limit.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int limit = VolumeLimiter.getLimitVolume(audioManager);
            return current >= limit;
        }

        return false;
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
    }
}
