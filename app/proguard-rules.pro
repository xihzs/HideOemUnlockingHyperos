# ProGuard rules for HideOEMUnlock
-keep class com.gwen.hideoemunlock.MainHook { *; }
-keepclassmembers class com.gwen.hideoemunlock.MainActivity {
    public boolean isModuleActive();
}
