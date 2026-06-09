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
        detailText.setPadding(0, dp(8), 0, dp(20));
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
        } else {
            stopService(intent);
        }
        updateUi();
    }

    private void updateUi() {
        boolean enabled = VolumeLimiter.isEnabled(this);
        boolean headset = VolumeLimiter.isHeadsetActive(audioManager);
        int max = audioManager == null ? 0 : audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int limit = audioManager == null ? 0 : VolumeLimiter.getLimitVolume(audioManager);
        int current = audioManager == null ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        statusText.setText(enabled ? "已启用" : "未启用");
        toggleButton.setText(enabled ? "关闭音量限制" : "启用音量限制");
        detailText.setText("仅在检测到有线耳机、USB 耳机或蓝牙耳机输出时生效。\n"
                + "当前耳机状态：" + (headset ? "已检测到" : "未检测到") + "\n"
                + "媒体音量：" + current + " / " + max + "，限制档位：" + limit + "，约 47% 以下。\n"
                + "说明：普通应用无法彻底锁死音量键，但会在后台服务中自动拉回。若系统杀后台，请在电池设置中允许后台运行。");
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
