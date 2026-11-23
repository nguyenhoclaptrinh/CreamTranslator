# ProGuard Rules for CreamTranslator (Release Hardening)
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve ML Kit Models and JNI bindings
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
