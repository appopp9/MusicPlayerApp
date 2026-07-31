# Proguard rules for MusicPlayer
-keepattributes *Annotation*
-keep class com.musicplayer.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
