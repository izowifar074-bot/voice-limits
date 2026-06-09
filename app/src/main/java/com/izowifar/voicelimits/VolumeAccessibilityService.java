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
        // Intentionally no-op. Do not modify volume from generic accessibility events,
        // otherwise some systems bounce the volume slider or keep repeating adjustments.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!VolumeLimiter.isEnabled(this)) return false;
        int keyCode = event.getKeyCode();

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return false;
        }

        ensureAudioManager();
        if (audioManager == null) return false;
        if (!VolumeLimiter.isHeadsetActive(audioManager)) return false;

        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(audioManager);

        // Conservative guard: block one step before the limit as well. This avoids stale
        // key-repeat readings where Android still processes repeats and jumps to max.
        int blockAt = Math.max(1, limit - 1);
        return current >= blockAt;
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
    }
}
