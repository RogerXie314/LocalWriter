# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ——————————————————————————————————————
# Room (数据库)
# ——————————————————————————————————————
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase { abstract *; }

# 保留所有数据实体和DAO
-keep class com.localwriter.data.** { *; }

# ——————————————————————————————————————
# epublib
# ——————————————————————————————————————
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**

# ——————————————————————————————————————
# iTextPDF
# ——————————————————————————————————————
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ——————————————————————————————————————
# Apache POI
# ——————————————————————————————————————
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**

# ——————————————————————————————————————
# juniversalchardet (编码检测)
# ——————————————————————————————————————
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**

# ——————————————————————————————————————
# AndroidX Biometric
# ——————————————————————————————————————
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ——————————————————————————————————————
# Kotlin 协程
# ——————————————————————————————————————
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ——————————————————————————————————————
# Gson (JSON 序列化)
# ——————————————————————————————————————
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# ——————————————————————————————————————
# XmlPull（epublib/kxml2 依赖，解决 R8 报错）
# ——————————————————————————————————————
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**
-keep class org.kxml2.** { *; }
-dontwarn org.kxml2.**

# ——————————————————————————————————————
# JSR-305 / Google Tink 注解（编译期注解，运行时不存在）
# ——————————————————————————————————————
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# ——————————————————————————————————————
# ViewBinding
# ——————————————————————————————————————
-keep class com.localwriter.databinding.** { *; }

# ——————————————————————————————————————
# 通用规则
# ——————————————————————————————————————
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
