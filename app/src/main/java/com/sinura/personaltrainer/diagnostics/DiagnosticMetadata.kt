package com.sinura.personaltrainer.diagnostics

import android.content.Context
import android.os.Build
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.local.FoundationGeneration
import java.io.File

object DiagnosticMetadata {
    /**
     * Where [LastCrashStore] lives. Named in backup_rules.xml and
     * data_extraction_rules.xml, so the name is a policy surface, not a detail.
     */
    const val DIRECTORY = "diagnostics"

    fun crashStore(context: Context): LastCrashStore =
        LastCrashStore(File(context.filesDir, DIRECTORY))

    fun collect(
        context: Context,
        crashStore: LastCrashStore = crashStore(context),
    ): Map<String, String> = mapOf(
        "appVersion" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE.toString(),
        "dbSchema" to FoundationGeneration.VERSION.toString(),
        "debug" to BuildConfig.DEBUG.toString(),
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "applicationId" to context.packageName,
        // The header says whether a prior process's fatal event is in this bundle, so a
        // reader knows to look for the previous-crash kind rather than guess from the list.
        "lastCrashAtMs" to (crashStore.load()?.atMs?.toString() ?: "none"),
    )

    fun bundle(
        context: Context,
        store: DiagnosticStore = DiagnosticRing.shared,
        crashStore: LastCrashStore = crashStore(context),
    ): String = DiagnosticRedaction.renderBundle(
        events = store.snapshot(),
        metadata = collect(context = context, crashStore = crashStore),
    )

    /** The owner's retention control: empties the ring and deletes the stored crash. */
    fun clear(
        context: Context,
        store: DiagnosticStore = DiagnosticRing.shared,
        crashStore: LastCrashStore = crashStore(context),
    ) {
        store.clear()
        crashStore.clear()
    }
}
