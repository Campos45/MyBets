# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- REGRAS DO PROGUARD ADICIONADAS PARA DEPLOY ---

# Room Database rules
-keepclassmembers class * {
    @androidx.room.Database *;
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao
-dontwarn androidx.room.paging.**

# GSON and Backup model serialization rules
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class pt.mybets.data.local.entity.** { *; }
-keep class pt.mybets.data.local.relations.** { *; }
-keep class pt.mybets.domain.model.** { *; }

# Google ML Kit Text Recognition rules
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.vision.text.**