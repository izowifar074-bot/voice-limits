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

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (event.getRepeatCount() > 0) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int limit = VolumeLimiter.getLimitVolume(this, audioManager);
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
