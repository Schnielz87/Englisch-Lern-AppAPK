# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class de.englischlernen.app.**$$serializer { *; }
-keepclassmembers class de.englischlernen.app.** { *** Companion; }
-keepclasseswithmembers class de.englischlernen.app.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Enum-Namen der App behalten (werden u. a. per Name gespeichert/gelesen)
-keepnames enum de.englischlernen.app.** { *; }
