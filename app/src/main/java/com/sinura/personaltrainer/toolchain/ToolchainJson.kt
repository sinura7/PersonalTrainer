package com.sinura.personaltrainer.toolchain

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** Shared Gson for the three pin files. BackupJson stays on its own builder. */
internal object ToolchainJson {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
}
