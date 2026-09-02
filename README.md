# Hide OEM Unlock - LSPosed / Vector Module

An Xposed / LSPosed / Vector module designed to hide the **OEM Unlocking** toggle in Developer Options across Android AOSP, Xiaomi HyperOS, and MIUI devices.

---

## 🚀 Features

- **Multi-tiered Hooking Strategy**:
  - Hooks `OemUnlockPreferenceController.isAvailable()` to return `false` (standard AOSP Settings framework mechanism).
  - Hooks `OemUnlockPreferenceController.displayPreference()` to dynamically hide (`setVisible(false)`) and remove the preference key.
  - Hooks `OemUnlockPreferenceController.updateState()` to suppress dynamic updates.
  - Fallback hooks for `DevelopmentSettingsDashboardFragment` (`onResume`, `updatePreferenceStates`).
- **Comprehensive Key Support**: Handles `oem_unlock_enable`, `oem_unlock`, and `enable_oem_unlock`.
- **Pre-configured Scope**: Automatically targets `com.android.settings` via manifest metadata.
- **Companion UI**: Clean Material 3 app indicating whether the module is currently active in LSPosed, along with one-tap shortcuts to open Developer Options and force-stop Settings.

---

## 🛠️ Project Structure

```
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Xposed module declaration & scope
│   │   ├── assets/
│   │   │   └── xposed_init              # Points to com.gwen.hideoemunlock.MainHook
│   │   ├── java/com/gwen/hideoemunlock/
│   │   │   ├── MainActivity.java        # Companion UI & status checker
│   │   │   └── MainHook.java            # Main Xposed hooking entry
│   │   └── res/                         # Material 3 layouts, strings, and icons
│   ├── build.gradle                     # App-level build file with Xposed API
│   └── proguard-rules.pro               # ProGuard keep rules for Xposed entry
├── build.gradle                         # Root Gradle config
├── settings.gradle                      # Project repository settings
└── README.md
```

---

## 📦 Building the Module

### Option 1: Using Android Studio
1. Open Android Studio.
2. Select **Open** and choose this project folder (`lshi`).
3. Allow Gradle to sync.
4. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Option 2: Using Terminal / Gradle
```bash
./gradlew assembleDebug
```
The output APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 Installation & Setup

1. **Install the APK on your device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Enable in LSPosed / Vector**:
   - Open **LSPosed** or **Vector** Manager.
   - Go to **Modules** → **Hide OEM Unlock**.
   - Toggle **Enable**.
   - Verify the scope is set to:
     - ☑ **Settings** (`com.android.settings`)

3. **Restart Settings**:
   ```bash
   adb shell am force-stop com.android.settings
   ```
   *(Or tap "Force Stop Settings" inside the Hide OEM Unlock companion app)*

4. **Verify**:
   - Open **Settings → Additional Settings → Developer Options**.
   - The **OEM Unlocking** option is now hidden.

---

## 🔍 Debugging & Logs

To check module logs and verify hook execution:

```bash
adb logcat -c
adb shell am force-stop com.android.settings
adb shell monkey -p com.android.settings 1
adb logcat -d | grep -i HideOEMUnlock
```

You should see log output similar to:
```
I HideOEMUnlock: Settings package detected (com.android.settings). Initializing hooks...
I HideOEMUnlock: Successfully hooked OemUnlockPreferenceController.isAvailable() -> return false
I HideOEMUnlock: Successfully hooked OemUnlockPreferenceController.displayPreference()
I HideOEMUnlock: Removed 'oem_unlock_enable' from PreferenceScreen.
```
