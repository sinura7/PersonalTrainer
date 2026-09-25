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

# slf4j without a binding. Temper Account's sign-in library (supabase gotrue-kt 2.6.1)
# brings Ktor 2.3.12, and Ktor brings slf4j-api 1.7.36 with no logging backend.
# slf4j's LoggerFactory.bind() names org.slf4j.impl.StaticLoggerBinder, a class only a
# backend supplies; when it is absent, bind() catches the NoClassDefFoundError and
# falls back to its no-op logger, which is what the unshrunk debug build does too.
# R8 refuses a program that names a class it cannot see, so without this line the
# release build stopped at minifyReleaseWithR8 from 21 September until audit X6
# found it (BR-1). The line is AGP's own suggestion (missing_rules.txt).
-dontwarn org.slf4j.impl.StaticLoggerBinder

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

# Temper Account sync rows go through Gson by reflection, and most of their fields (id,
# status, title, notes, ...) carry no @SerializedName, so a renamed field is a renamed JSON
# key and the server rejects the row. Same reason as data.backup above.
-keep class com.sinura.personaltrainer.data.sync.** { *; }

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
