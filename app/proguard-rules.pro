-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class kotlin.Metadata { *; }
-keep class androidx.compose.runtime.** { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <init>(...); }
-keepclassmembers class * { @kotlinx.serialization.SerialName <fields>; }

-keep class com.mochikanban.app.domain.** { *; }
-keep class com.mochikanban.app.data.db.entity.** { *; }
-keep class com.mochikanban.app.sync.net.** { *; }

-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Google sign-in: Credential Manager + googleid + play-services-auth.
# These rely on reflection/JNI that R8 can break in a minified release.
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-dontwarn com.google.android.gms.**

# Glance widget reflection
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
