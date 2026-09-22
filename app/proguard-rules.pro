# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.AndroidEntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.HiltAndroidApp class *

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Strip Logcat invocations in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Google ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**

# WorkManager & Hilt Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.hilt.work.** { *; }

# Tink / Security Crypto
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}

# Room & Domain Entities
-keep class com.docscanner.app.data.local.entity.** { *; }
-keep class com.docscanner.app.domain.model.** { *; }
-keep class com.scanly.data.storage.** { *; }


