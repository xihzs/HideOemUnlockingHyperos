package com.gwen.hideoemunlock;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HideOEMUnlock";
    public static final String PACKAGE_NAME = "com.gwen.hideoemunlock";
    public static final String PREF_NAME = "config";
    public static final String KEY_HIDE_OEM = "hide_oem_unlock";
    public static final String KEY_HIDE_MI_UNLOCK = "hide_mi_unlock";
    public static final String PROVIDER_URI = "content://" + PACKAGE_NAME + ".provider";

    private static final String[] OEM_CONTROLLERS = {
            "com.android.settings.development.OemUnlockPreferenceController",
            "com.android.settings.development.MiuiOemUnlockPreferenceController"
    };

    private static final String[] MI_UNLOCK_CONTROLLERS = {
            "com.android.settings.development.MiuiUnlockStatusPreferenceController",
            "com.android.settings.development.MiuiUnlockStatusController",
            "com.android.settings.development.ApplyUnlockStatusPreferenceController",
            "com.android.settings.development.FastbootUnlockStatusPreferenceController"
    };

    private static Context sAppContext = null;
    private static XSharedPreferences sPrefs = null;

    private static boolean sHideOemCached = true;
    private static boolean sHideMiUnlockCached = true;
    private static long sLastFetchTime = 0;

    private static synchronized XSharedPreferences getPrefs() {
        if (sPrefs == null) {
            sPrefs = new XSharedPreferences(PACKAGE_NAME, PREF_NAME);
            sPrefs.makeWorldReadable();
        } else {
            sPrefs.reload();
        }
        return sPrefs;
    }

    public static synchronized void refreshConfig(Context context) {
        long now = System.currentTimeMillis();
        if (context == null) {
            context = sAppContext;
        } else {
            sAppContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        }

        // Cache for 500ms to avoid high-frequency IPC on each layout pass
        if (now - sLastFetchTime < 500) {
            return;
        }

        // 1. Try ContentProvider query (Works across all Android versions & SELinux)
        if (context != null) {
            try {
                ContentResolver resolver = context.getContentResolver();
                if (resolver != null) {
                    Bundle bundle = resolver.call(
                            Uri.parse(PROVIDER_URI),
                            ConfigProvider.METHOD_GET_CONFIG,
                            null,
                            null
                    );
                    if (bundle != null) {
                        sHideOemCached = bundle.getBoolean(KEY_HIDE_OEM, true);
                        sHideMiUnlockCached = bundle.getBoolean(KEY_HIDE_MI_UNLOCK, true);
                        sLastFetchTime = now;
                        Log.i(TAG, "Refreshed config via ContentProvider: HideOEM=" + sHideOemCached + ", HideMiUnlock=" + sHideMiUnlockCached);
                        return;
                    }
                }
            } catch (Throwable t) {
                Log.d(TAG, "ContentProvider query failed: " + t.getMessage());
            }
        }

        // 2. Try XSharedPreferences fallback
        try {
            XSharedPreferences xsp = getPrefs();
            if (xsp.contains(KEY_HIDE_OEM) || xsp.contains(KEY_HIDE_MI_UNLOCK)) {
                sHideOemCached = xsp.getBoolean(KEY_HIDE_OEM, true);
                sHideMiUnlockCached = xsp.getBoolean(KEY_HIDE_MI_UNLOCK, true);
                sLastFetchTime = now;
                Log.i(TAG, "Refreshed config via XSharedPreferences: HideOEM=" + sHideOemCached + ", HideMiUnlock=" + sHideMiUnlockCached);
                return;
            }
        } catch (Throwable ignored) {
        }

        sLastFetchTime = now;
    }

    public static boolean isHideOemEnabled() {
        refreshConfig(sAppContext);
        return sHideOemCached;
    }

    public static boolean isHideMiUnlockEnabled() {
        refreshConfig(sAppContext);
        return sHideMiUnlockCached;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 1. Hook own app to report active module status to MainActivity UI
        if (PACKAGE_NAME.equals(lpparam.packageName)) {
            hookSelfStatus(lpparam.classLoader);
            return;
        }

        // 2. Hook Settings
        if ("com.android.settings".equals(lpparam.packageName) || "com.xiaomi.misettings".equals(lpparam.packageName)) {
            Log.i(TAG, "Settings process detected: " + lpparam.packageName + " (" + lpparam.processName + "). Initializing hooks...");
            hookSettings(lpparam.classLoader);
        }
    }

    private void hookSelfStatus(ClassLoader classLoader) {
        try {
            Class<?> mainActivityClass = XposedHelpers.findClassIfExists(
                    PACKAGE_NAME + ".MainActivity",
                    classLoader
            );
            if (mainActivityClass != null) {
                XposedHelpers.findAndHookMethod(
                        mainActivityClass,
                        "isModuleActive",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.setResult(true);
                            }
                        }
                );
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook self-status check", t);
        }
    }

    private void hookSettings(ClassLoader classLoader) {
        // Capture Application context
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sAppContext = (Context) param.thisObject;
                            refreshConfig(sAppContext);
                        }
                    }
            );
        } catch (Throwable ignored) {
        }

        // Layer 1: Hook Controllers
        for (String controllerName : OEM_CONTROLLERS) {
            hookController(classLoader, controllerName, MainHook::isHideOemEnabled);
        }
        for (String controllerName : MI_UNLOCK_CONTROLLERS) {
            hookController(classLoader, controllerName, MainHook::isHideMiUnlockEnabled);
        }

        // Layer 2: Hook PreferenceGroup.addPreference to intercept items at addition time
        hookPreferenceGroup(classLoader);

        // Layer 3: Hook Preference.onBindViewHolder to hide UI View directly if attached
        hookPreferenceViewBinding(classLoader);

        // Layer 4: Hook Dashboard Fragments
        hookDashboardFragment(classLoader, "com.android.settings.development.DevelopmentSettingsDashboardFragment");
        hookDashboardFragment(classLoader, "com.android.settings.development.DevelopmentSettings");
        hookDashboardFragment(classLoader, "com.android.settings.Settings$DevelopmentSettingsDashboardActivity");
    }

    private interface ToggleCheck {
        boolean isEnabled();
    }

    private void hookController(ClassLoader classLoader, String className, ToggleCheck toggleCheck) {
        try {
            Class<?> controllerClass = XposedHelpers.findClassIfExists(className, classLoader);
            if (controllerClass == null) return;

            // Hook isAvailable()
            try {
                XposedHelpers.findAndHookMethod(
                        controllerClass,
                        "isAvailable",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                refreshConfig(extractContext(param.thisObject));
                                if (toggleCheck.isEnabled()) {
                                    param.setResult(false);
                                }
                            }
                        }
                );
                Log.i(TAG, "Hooked isAvailable() on " + className);
            } catch (Throwable ignored) {
            }

            // Hook displayPreference
            try {
                Class<?> prefScreenClass = findPreferenceScreenClass(classLoader);
                if (prefScreenClass != null) {
                    XposedHelpers.findAndHookMethod(
                            controllerClass,
                            "displayPreference",
                            prefScreenClass,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    refreshConfig(extractContext(param.thisObject));
                                    filterPreferenceGroup(param.args[0]);
                                }
                            }
                    );
                    Log.i(TAG, "Hooked displayPreference() on " + className);
                }
            } catch (Throwable ignored) {
            }

            // Hook updateState
            try {
                Class<?> prefClass = findPreferenceClass(classLoader);
                if (prefClass != null) {
                    XposedHelpers.findAndHookMethod(
                            controllerClass,
                            "updateState",
                            prefClass,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    refreshConfig(extractContext(param.thisObject));
                                    if (shouldHidePreference(param.args[0])) {
                                        hidePreferenceDirectly(param.args[0]);
                                    }
                                }
                            }
                    );
                    Log.i(TAG, "Hooked updateState() on " + className);
                }
            } catch (Throwable ignored) {
            }

        } catch (Throwable t) {
            Log.e(TAG, "Error hooking controller " + className, t);
        }
    }

    private void hookPreferenceGroup(ClassLoader classLoader) {
        String[] groupClasses = {
                "androidx.preference.PreferenceGroup",
                "android.preference.PreferenceGroup"
        };
        String[] prefClasses = {
                "androidx.preference.Preference",
                "android.preference.Preference"
        };

        for (int i = 0; i < groupClasses.length; i++) {
            try {
                Class<?> groupClass = XposedHelpers.findClassIfExists(groupClasses[i], classLoader);
                Class<?> prefClass = XposedHelpers.findClassIfExists(prefClasses[i], classLoader);
                if (groupClass != null && prefClass != null) {
                    XposedHelpers.findAndHookMethod(
                            groupClass,
                            "addPreference",
                            prefClass,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    Object pref = param.args[0];
                                    refreshConfig(extractContext(pref));
                                    if (shouldHidePreference(pref)) {
                                        hidePreferenceDirectly(pref);
                                        param.setResult(false);
                                        Log.i(TAG, "Blocked addPreference for matched unlock preference.");
                                    }
                                }
                            }
                    );
                    Log.i(TAG, "Hooked addPreference on " + groupClasses[i]);
                }
            } catch (Throwable t) {
                Log.d(TAG, "Could not hook " + groupClasses[i] + ".addPreference: " + t.getMessage());
            }
        }
    }

    private void hookPreferenceViewBinding(ClassLoader classLoader) {
        try {
            Class<?> prefClass = XposedHelpers.findClassIfExists("androidx.preference.Preference", classLoader);
            Class<?> holderClass = XposedHelpers.findClassIfExists("androidx.preference.PreferenceViewHolder", classLoader);
            if (prefClass != null && holderClass != null) {
                XposedHelpers.findAndHookMethod(
                        prefClass,
                        "onBindViewHolder",
                        holderClass,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                refreshConfig(extractContext(param.thisObject));
                                if (shouldHidePreference(param.thisObject)) {
                                    collapsePreferenceView(param.args[0]);
                                }
                            }
                        }
                );
                Log.i(TAG, "Hooked onBindViewHolder on androidx.preference.Preference");
            }
        } catch (Throwable t) {
            Log.d(TAG, "Could not hook onBindViewHolder: " + t.getMessage());
        }
    }

    private static void collapsePreferenceView(Object holder) {
        if (holder == null) return;
        try {
            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
            if (itemView != null) {
                itemView.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    lp.width = 0;
                    itemView.setLayoutParams(lp);
                }
                Log.i(TAG, "Collapsed preference view in RecyclerView.");
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookDashboardFragment(ClassLoader classLoader, String className) {
        try {
            Class<?> fragmentClass = XposedHelpers.findClassIfExists(className, classLoader);
            if (fragmentClass == null) return;

            XC_MethodHook fragmentFilterHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        refreshConfig(extractContext(param.thisObject));
                        Object screen = XposedHelpers.callMethod(param.thisObject, "getPreferenceScreen");
                        if (screen != null) {
                            filterPreferenceGroup(screen);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };

            // Hook onResume
            try {
                XposedHelpers.findAndHookMethod(fragmentClass, "onResume", fragmentFilterHook);
                Log.i(TAG, "Hooked onResume on " + className);
            } catch (Throwable ignored) {
            }

            // Hook updatePreferenceStates
            try {
                XposedHelpers.findAndHookMethod(fragmentClass, "updatePreferenceStates", fragmentFilterHook);
                Log.i(TAG, "Hooked updatePreferenceStates on " + className);
            } catch (Throwable ignored) {
            }

            // Hook setPreferenceScreen
            try {
                XposedHelpers.findAndHookMethod(fragmentClass, "setPreferenceScreen", findPreferenceScreenClass(classLoader), fragmentFilterHook);
                Log.i(TAG, "Hooked setPreferenceScreen on " + className);
            } catch (Throwable ignored) {
            }

        } catch (Throwable t) {
            Log.e(TAG, "Failed hooking fragment " + className, t);
        }
    }

    public static boolean shouldHidePreference(Object pref) {
        if (pref == null) return false;
        try {
            // 1. Check Key
            String key = (String) XposedHelpers.callMethod(pref, "getKey");
            if (key != null) {
                String lowerKey = key.toLowerCase();
                if (isHideOemEnabled()) {
                    if (lowerKey.contains("oem_unlock") || lowerKey.equals("oem_unlock_enable") || lowerKey.contains("enable_oem_unlock")) {
                        Log.i(TAG, "Matched OEM preference key: " + key);
                        return true;
                    }
                }
                if (isHideMiUnlockEnabled()) {
                    if (lowerKey.contains("unlock_status") || lowerKey.contains("miui_unlock")
                            || lowerKey.contains("mi_unlock") || lowerKey.contains("apply_unlock")
                            || lowerKey.contains("flash_lock") || lowerKey.contains("fastboot_unlock")
                            || lowerKey.contains("lock_status") || lowerKey.contains("device_unlock")) {
                        Log.i(TAG, "Matched Mi Unlock preference key: " + key);
                        return true;
                    }
                }
            }

            // 2. Check Title
            CharSequence title = (CharSequence) XposedHelpers.callMethod(pref, "getTitle");
            if (title != null) {
                String titleStr = title.toString().toLowerCase();
                if (isHideOemEnabled()) {
                    if (titleStr.contains("oem unlock") || titleStr.contains("oem unlocking")
                            || titleStr.contains("oem解锁") || titleStr.contains("oem 解锁")) {
                        Log.i(TAG, "Matched OEM preference title: " + title);
                        return true;
                    }
                }
                if (isHideMiUnlockEnabled()) {
                    if (titleStr.contains("mi unlock") || titleStr.contains("miui unlock")
                            || titleStr.contains("unlock status") || titleStr.contains("设备解锁状态")
                            || titleStr.contains("小米解锁状态") || titleStr.contains("解锁状态")
                            || titleStr.contains("fastboot unlock") || titleStr.contains("bootloader unlock")) {
                        Log.i(TAG, "Matched Mi Unlock preference title: " + title);
                        return true;
                    }
                }
            }

            // 3. Check Summary / Subtitle
            CharSequence summary = (CharSequence) XposedHelpers.callMethod(pref, "getSummary");
            if (summary != null) {
                String sumStr = summary.toString().toLowerCase();
                if (isHideMiUnlockEnabled()) {
                    if (sumStr.contains("check if the device is locked") || sumStr.contains("设备是否锁定")
                            || sumStr.contains("绑定账号和设备") || sumStr.contains("account and device")) {
                        Log.i(TAG, "Matched Mi Unlock summary: " + summary);
                        return true;
                    }
                }
            }

            // 4. Check Intent Target
            Intent intent = (Intent) XposedHelpers.callMethod(pref, "getIntent");
            if (intent != null) {
                String intentStr = intent.toString().toLowerCase();
                if (isHideMiUnlockEnabled()) {
                    if (intentStr.contains("unlockstatus") || intentStr.contains("applyunlock")
                            || intentStr.contains("miuiunlock") || intentStr.contains("unlock_status")) {
                        Log.i(TAG, "Matched Mi Unlock intent: " + intentStr);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in shouldHidePreference", t);
        }
        return false;
    }

    private static void filterPreferenceGroup(Object group) {
        if (group == null) return;
        try {
            int count = (int) XposedHelpers.callMethod(group, "getPreferenceCount");
            for (int i = count - 1; i >= 0; i--) {
                Object pref = XposedHelpers.callMethod(group, "getPreference", i);
                if (pref == null) continue;

                // Check if this preference is itself a group
                try {
                    int childCount = (int) XposedHelpers.callMethod(pref, "getPreferenceCount");
                    if (childCount > 0) {
                        filterPreferenceGroup(pref);
                    }
                } catch (Throwable ignored) {
                }

                if (shouldHidePreference(pref)) {
                    hidePreferenceDirectly(pref);
                    try {
                        XposedHelpers.callMethod(group, "removePreference", pref);
                        Log.i(TAG, "Removed preference from PreferenceGroup.");
                    } catch (Throwable t) {
                        Log.d(TAG, "removePreference failed, setVisible(false) applied.");
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in filterPreferenceGroup", t);
        }
    }

    private static void hidePreferenceDirectly(Object preference) {
        if (preference == null) return;
        try {
            XposedHelpers.callMethod(preference, "setVisible", false);
            Log.i(TAG, "Set preference visibility to false.");
        } catch (Throwable ignored) {
        }
    }

    private static Context extractContext(Object obj) {
        if (obj == null) return sAppContext;
        try {
            if (obj instanceof Context) return (Context) obj;
            Object ctx = XposedHelpers.callMethod(obj, "getContext");
            if (ctx instanceof Context) return (Context) ctx;
        } catch (Throwable ignored) {
        }
        try {
            Object ctx = XposedHelpers.getObjectField(obj, "mContext");
            if (ctx instanceof Context) return (Context) ctx;
        } catch (Throwable ignored) {
        }
        return sAppContext;
    }

    private static Class<?> findPreferenceScreenClass(ClassLoader classLoader) {
        Class<?> clazz = XposedHelpers.findClassIfExists("androidx.preference.PreferenceScreen", classLoader);
        if (clazz == null) {
            clazz = XposedHelpers.findClassIfExists("android.preference.PreferenceScreen", classLoader);
        }
        return clazz;
    }

    private static Class<?> findPreferenceClass(ClassLoader classLoader) {
        Class<?> clazz = XposedHelpers.findClassIfExists("androidx.preference.Preference", classLoader);
        if (clazz == null) {
            clazz = XposedHelpers.findClassIfExists("android.preference.Preference", classLoader);
        }
        return clazz;
    }
}
