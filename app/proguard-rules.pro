# Proguard rules for RotCount
-keep class com.suhan.reelcounter.data.** { *; }
-keep class com.suhan.reelcounter.logic.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Industry-standard release hardening: Strip all debug logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
