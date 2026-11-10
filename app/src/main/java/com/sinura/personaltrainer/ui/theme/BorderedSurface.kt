package com.sinura.personaltrainer.ui.theme

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.sinura.personaltrainer.BuildConfig

val LocalBorderedSurfaceDepth = staticCompositionLocalOf { 0 }

private var nestingWarningLogged = false

@Composable
internal fun warnBorderedSurfaceNesting(surface: String) {
    if (!BuildConfig.DEBUG || nestingWarningLogged) return
    if (LocalBorderedSurfaceDepth.current > 0) {
        nestingWarningLogged = true
        Log.w("TemperRefresh", "Bordered surface $surface composed inside another bordered stack")
    }
}

@Composable
internal fun BorderSurfaceScope(content: @Composable () -> Unit) {
    val depth = LocalBorderedSurfaceDepth.current
    CompositionLocalProvider(LocalBorderedSurfaceDepth provides depth + 1) {
        content()
    }
}
