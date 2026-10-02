package com.sinura.personaltrainer.ui.settings

import androidx.compose.runtime.compositionLocalOf

/** Opens Settings → Permissions from workout or reminder flows. */
val LocalOpenPermissionsSettings = compositionLocalOf<() -> Unit> { {} }
