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
###############################
## GLOBAL AND KOTLIN SUPPORT ##
###############################

# Keep Kotlin metadata for reflection
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Keep enum names
-keepclassmembers enum * { *; }

################################
## ANDROIDX / UI / LIFECYCLE  ##
################################

# Core, AppCompat, Activity, Fragment, ViewModel, LiveData
-keep class androidx.core.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.fragment.app.** { *; }

# Lifecycle & ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# Viewpager2 & ConstraintLayout
-dontwarn androidx.viewpager2.**
-dontwarn androidx.constraintlayout.**

#####################################
## GOOGLE PLAY SERVICES / LOCATION ##
#####################################

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

###################################
## MATERIAL DESIGN / UI LIBRARIES ##
###################################

-dontwarn com.google.android.material.**

##########################################
## FIREBASE REALTIME DATABASE SUPPORT   ##
##########################################

# Keep Firebase SDK internals
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Firebase model classes
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}

###############################
## RETROFIT + OKHTTP + GSON  ##
###############################

# Retrofit interfaces
-keep interface retrofit2.* { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# OkHttp + Logging
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn okhttp3.logging.**

# Gson serialization
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class sun.misc.Unsafe { *; }   # required by Gson

# Keep models annotated with SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

#############
## ROOM DB ##
#############

-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Database / DAO / Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }

###########
## GLIDE ##
###########

# Glide classes
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# Keep generated API
-keep @com.bumptech.glide.annotation.GlideModule class * { *; }
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }

###################################
## SDP / SSP (Dimension Helpers) ##
###################################

-keep class com.intuit.sdp.** { *; }
-keep class com.intuit.ssp.** { *; }

##########################################
## OPTIONAL LOG CLEANUP FOR RELEASE APK ##
##########################################

# Remove debug logs from release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
