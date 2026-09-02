package com.gwen.hideoemunlock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.DataOutputStream;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    public static final String PREF_NAME = "config";
    public static final String KEY_HIDE_OEM = "hide_oem_unlock";
    public static final String KEY_HIDE_MI_UNLOCK = "hide_mi_unlock";

    private SharedPreferences mPrefs;
    private MaterialSwitch mSwitchOem;
    private MaterialSwitch mSwitchMiUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        MaterialCardView statusCard = findViewById(R.id.card_status);
        TextView statusText = findViewById(R.id.tv_status);
        TextView statusDesc = findViewById(R.id.tv_status_desc);
        mSwitchOem = findViewById(R.id.switch_hide_oem);
        mSwitchMiUnlock = findViewById(R.id.switch_hide_mi_unlock);
        Button btnOpenSettings = findViewById(R.id.btn_open_settings);
        Button btnForceStopSettings = findViewById(R.id.btn_force_stop_settings);

        // Status Card
        boolean active = isModuleActive();
        if (active) {
            statusText.setText(R.string.status_active);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green_active));
            statusDesc.setText(R.string.status_active_desc);
            statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.green_active));
        } else {
            statusText.setText(R.string.status_inactive);
            statusText.setTextColor(ContextCompat.getColor(this, R.color.red_inactive));
            statusDesc.setText(R.string.status_inactive_desc);
            statusCard.setStrokeColor(ContextCompat.getColor(this, R.color.red_inactive));
        }

        // Initialize Switches
        boolean hideOem = mPrefs.getBoolean(KEY_HIDE_OEM, true);
        boolean hideMiUnlock = mPrefs.getBoolean(KEY_HIDE_MI_UNLOCK, true);

        mSwitchOem.setChecked(hideOem);
        mSwitchMiUnlock.setChecked(hideMiUnlock);

        mSwitchOem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(KEY_HIDE_OEM, isChecked).apply();
            makePrefsWorldReadable();
            Toast.makeText(this, R.string.pref_saved_notice, Toast.LENGTH_SHORT).show();
        });

        mSwitchMiUnlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(KEY_HIDE_MI_UNLOCK, isChecked).apply();
            makePrefsWorldReadable();
            Toast.makeText(this, R.string.pref_saved_notice, Toast.LENGTH_SHORT).show();
        });

        makePrefsWorldReadable();

        // Button Actions
        btnOpenSettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                    Toast.makeText(this, "Could not open Settings", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnForceStopSettings.setOnClickListener(v -> {
            boolean success = tryForceStopSettingsWithRoot();
            if (success) {
                Toast.makeText(this, "Settings force-stopped via root", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Root not available. Please force stop Settings manually in App Info", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Set world-readable permissions so XSharedPreferences / LSPosed can read the config file.
     */
    private void makePrefsWorldReadable() {
        try {
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            File prefsFile = new File(prefsDir, PREF_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hooked dynamically by MainHook in LSPosed/Xposed framework to return true when active.
     */
    public boolean isModuleActive() {
        return false;
    }

    private boolean tryForceStopSettingsWithRoot() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("am force-stop com.android.settings\n");
            os.writeBytes("am force-stop com.xiaomi.misettings\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}
