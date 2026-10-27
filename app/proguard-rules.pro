# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room entity classes
-keep class com.example.chronox.data.** { *; }

# Keep alarm model class
-keep class com.example.chronox.alarm.Alarm { *; }

# Keep world clock model
-keep class com.example.chronox.worldclock.WorldClock { *; }
