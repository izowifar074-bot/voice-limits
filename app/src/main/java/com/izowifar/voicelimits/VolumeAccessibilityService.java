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
        // Intentionally no-op. Do not modify volume from generic accessibility events.
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

        // Convert long-press on either volume key into a single tap:
        // allow the first DOWN event, then consume all repeat DOWN events.
        // This prevents an accidental long-press on volume-up from racing to max volume.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) {
            return true;
        }

        // Keep the upper guard: if volume is already at/near the limit, block volume-up entirely.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int limit = VolumeLimiter.getLimitVolume(audioManager);
            int blockAt = Math.max(1, limit - 1);
            return current >= blockAt;
        }

        return false;
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
    }
}
