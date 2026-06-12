# MiMo Remote ProGuard Rules

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.mimo.remote.**$$serializer { *; }
-keepclassmembers class com.mimo.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.mimo.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }
