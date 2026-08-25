package com.sinura.personaltrainer.diagnostics

import android.content.Context
import android.os.Build
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.local.FoundationGeneration

object DiagnosticMetadata {
    fun collect(context: Context): Map<String, String> = mapOf(
        "appVersion" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE.toString(),
        "dbSchema" to FoundationGeneration.VERSION.toString(),
        "debug" to BuildConfig.DEBUG.toString(),
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "applicationId" to context.packageName,
    )

    fun bundle(context: Context, store: DiagnosticStore = DiagnosticRing.shared): String =
        DiagnosticRedaction.renderBundle(store.snapshot(), collect(context))
}
