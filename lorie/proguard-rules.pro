-keepattributes SourceFile,LineNumberTable

# Keep classes/members referenced from JNI (FindClass/GetMethodID/RegisterNatives) + main entrypoint
-keep class com.termux.x11.LorieView {
    native <methods>;
    void resetIme();
    public void showKeyboard();
    public void hideKeyboard();
}

-keep class com.termux.x11.CmdEntryPoint {
    public static void main(java.lang.String[]);
}

# CmdEntryPoint reaches these hidden framework classes via reflection; they
# exist at runtime but aren't in the public SDK stub jar R8 resolves against.
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.IActivityManager
-dontwarn android.content.IIntentReceiver
-dontwarn android.content.IIntentReceiver$Stub
-dontwarn android.content.IIntentSender
-dontwarn android.content.pm.IPackageManager

-keepclassmembers class com.termux.x11.** {
    void performReceive(android.content.Intent, int, java.lang.String, android.os.Bundle, boolean, boolean, int);
}

# Keep Preference subclasses' onSetInitialValue for reflective calls
-keepclassmembers class * extends android.preference.Preference {
    void onSetInitialValue(boolean, java.lang.Object);
}

# Preserve FloatingButtonService (called by system)
-keep class com.termux.x11.floating.FloatingButtonService { *; }

# Preserve FloatingButtonListener interface
-keep interface com.termux.x11.floating.FloatingButtonListener { *; }

# Preserve FloatingButtonManager singleton
-keep class com.termux.x11.floating.FloatingButtonManager {
    public static com.termux.x11.floating.FloatingButtonManager getInstance(android.content.Context);
    public void setLorieView(com.termux.x11.LorieView);
    public void clearLorieView();
    public void toggleKeyboard();
    public void registerListener(com.termux.x11.floating.FloatingButtonListener);
}

# Preserve OverlayPermissionHelper static methods
-keep class com.termux.x11.utils.OverlayPermissionHelper {
    public static boolean hasOverlayPermission(android.app.Activity);
    public static void requestOverlayPermission(android.app.Activity, int);
}

# Preserve LoriePreferences PrefsProto and inner classes (accessed via reflection)
-keep class com.termux.x11.LoriePreferences$PrefsProto { *; }
-keep class com.termux.x11.LoriePreferences$PrefsProto$* { *; }
-keep class com.termux.x11.LoriePreferences { *; }
