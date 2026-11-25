# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# SLF4J - Keep logger classes (used by Ktor)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
-keepclassmembers class org.slf4j.** { *; }

# Ktor - Keep HTTP client classes
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dnnypck.capacitiesquicknotepro.**$$serializer { *; }
-keep,includedescriptorclasses class com.dnnypck.targets.**$$serializer { *; }
-keepclassmembers class com.dnnypck.capacitiesquicknotepro.** {
    *** Companion;
}
-keepclassmembers class com.dnnypck.targets.** {
    *** Companion;
}
-keepclasseswithmembers class com.dnnypck.capacitiesquicknotepro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.dnnypck.targets.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes for serialization
-keep @kotlinx.serialization.Serializable class * { *; }

# Room - Keep database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose - Keep reflection for UI
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep app classes
-keep class com.dnnypck.capacitiesquicknotepro.** { *; }
-keep class com.dnnypck.targets.** { *; }
-keepclassmembers class com.dnnypck.capacitiesquicknotepro.** { *; }
-keepclassmembers class com.dnnypck.targets.** { *; }

# Tink / EncryptedSharedPreferences
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
