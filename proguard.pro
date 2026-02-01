# The rules from AOSP are located in proguard.flags file, we can just maintain Lawnchair related rules here.

-verbose
-allowaccessmodification
-repackageclasses
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Remove some Kotlin overhead
-processkotlinnullchecks remove

-keep,allowshrinking,allowoptimization class com.android.launcher3.** {*;}

# Lawnchair specific rules.
-keep class app.lawnchair.LawnchairProto$* { *; }
-keep class app.lawnchair.LawnchairApp { *; }
-keep class app.lawnchair.LawnchairLauncher { *; }
-keep class app.lawnchair.compatlib.** { *; }

-dontwarn com.skydoves.balloon.*
