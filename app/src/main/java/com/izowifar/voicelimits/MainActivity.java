package com.izowifar.voicelimits;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView detailText;
    private Button toggleButton;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        buildUi();
        requestRuntimePermissions();
        requestBatteryOptimizationPermission();
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void buildUi() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("Voice Limits");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        detailText = new TextView(this);
        detailText.setTextSize(14);
        detailText.setGravity(Gravity.CENTER);
        detailText.setPadding(0, dp(8), 0, dp(16));
        root.addView(detailText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        toggleButton = new Button(this);
        toggleButton.setAllCaps(false);
        toggleButton.setTextSize(18);
        toggleButton.setOnClickListener(v -> toggleService());
        root.addView(toggleButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        LinearLayout limitRow = new LinearLayout(this);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        limitRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        rowParams.topMargin = dp(12);
        root.addView(limitRow, rowParams);

        Button downButton = new Button(this);
        downButton.setAllCaps(false);
        downButton.setText("上限 -");
        downButton.setOnClickListener(v -> changeLimit(-1));
        limitRow.addView(downButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Button currentButton = new Button(this);
        currentButton.setAllCaps(false);
        currentButton.setText("设为当前音量");
        currentButton.setOnClickListener(v -> setLimitToCurrentVolume());
        limitRow.addView(currentButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));

        Button upButton = new Button(this);
        upButton.setAllCaps(false);
        upButton.setText("上限 +");
        upButton.setOnClickListener(v -> changeLimit(1));
        limitRow.addView(upButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Button accessibilityButton = new Button(this);
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setText("打开无障碍设置");
        accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        LinearLayout.LayoutParams accessibilityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        accessibilityParams.topMargin = dp(12);
        root.addView(accessibilityButton, accessibilityParams);

        Button settingsButton = new Button(this);
        settingsButton.setAllCaps(false);
        settingsButton.setText("打开应用后台/电池设置");
        settingsButton.setOnClickListener(v -> openAppSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        settingsParams.topMargin = dp(12);
        root.addView(settingsButton, settingsParams);
    }

    private void toggleService() {
        boolean next = !VolumeLimiter.isEnabled(this);
        VolumeLimiter.setEnabled(this, next);
        Intent intent = new Intent(this, VolumeLimitService.class);
        intent.setAction(next ? VolumeLimitService.ACTION_START : VolumeLimitService.ACTION_STOP);
        if (next) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            openAccessibilitySettings();
        } else {
            VolumeLimitService.cancelKeepAlive(this);
            stopService(intent);
        }
        updateUi();
    }

    private void changeLimit(int delta) {
        if (audioManager == null) return;
        int limit = VolumeLimiter.getLimitVolume(this, audioManager);
        VolumeLimiter.setLimitVolume(this, audioManager, limit + delta);
        clampCurrentIfNeeded();
        updateUi();
    }

    private void setLimitToCurrentVolume() {
        if (audioManager == null) return;
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        VolumeLimiter.setLimitVolume(this, audioManager, current);
        updateUi();
    }

    private void clampCurrentIfNeeded() {
        if (audioManager != null && VolumeLimiter.isHeadsetActive(audioManager)) {
            VolumeLimiter.clampIfNeeded(this, audioManager);
        }
    }

    private void updateUi() {
        boolean enabled = VolumeLimiter.isEnabled(this);
        boolean headset = VolumeLimiter.isHeadsetActive(audioManager);
        int max = audioManager == null ? 0 : audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int limit = audioManager == null ? 0 : VolumeLimiter.getLimitVolume(this, audioManager);
        int current = audioManager == null ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percent = max <= 0 ? 0 : Math.round(limit * 100f / max);

        statusText.setText(enabled ? "已启用" : "未启用");
        toggleButton.setText(enabled ? "关闭音量限制" : "启用音量限制");
        detailText.setText("仅在检测到有线耳机、USB 耳机或蓝牙耳机输出时生效。\n"
                + "当前耳机状态：" + (headset ? "已检测到" : "未检测到") + "\n"
                + "媒体音量：" + current + " / " + max + "\n"
                + "限制档位：" + limit + " / " + max + "，约 " + percent + "%\n\n"
                + "到达限制档位后，耳机状态下音量上键会被拦截；长按重复事件也会被拦截。\n"
                + "建议先把耳机音量调到舒服的位置，再点“设为当前音量”。");
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4701);
        }
    }

    private void requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
                    openAppSettings();
                }
            }
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
