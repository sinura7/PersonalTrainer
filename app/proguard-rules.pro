# Temper release hardening (P12.3 / FND-029).
# Keep the portable backup contract, Room entities, and kotlinx.serialization
# names. Do not keep log messages or workout payloads — those never belong
# in a mapping-independent keep rule.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exception
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# kotlinx.serialization — generated serializers and annotated models.
-keep,includedescriptorclasses class com.sinura.personaltrainer.**$$serializer { *; }
-keepclassmembers class com.sinura.personaltrainer.** {
    *** Companion;
}
-keepclasseswithmembers class com.sinura.personaltrainer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.sinura.personaltrainer.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Gson / backup codec field names used by portable files.
-keep class com.sinura.personaltrainer.data.backup.** { *; }

# Diagnostics remain user-triggered; keep the redaction types readable.
-keep class com.sinura.personaltrainer.diagnostics.** { *; }
# DiagnosticRedaction.appFrames keeps frames whose className starts with
# the app package. Shrinking must not flatten that prefix away.
-keeppackagenames com.sinura.personaltrainer.**

# Compose / ViewModel factories used by reflection.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
