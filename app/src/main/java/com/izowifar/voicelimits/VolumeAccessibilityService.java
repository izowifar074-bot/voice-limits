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
        // Intentionally no-op. Volume keys are handled only in onKeyEvent.
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

        // Take over volume keys completely while enabled and a headset is active.
        // Android's default long-press handling is not allowed to see these events,
        // otherwise some systems can still race the volume to max.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            adjustOneStep(keyCode);
        }
        return true;
    }

    private void adjustOneStep(int keyCode) {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int limit = VolumeLimiter.getLimitVolume(audioManager);
        int target = current;

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            target = Math.min(current + 1, limit);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            target = Math.max(current - 1, 0);
        }

        target = Math.max(0, Math.min(target, max));
        if (target != current) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        }
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
    }
}
