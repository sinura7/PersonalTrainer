package com.sinura.personaltrainer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.input.TextFieldValue

/** Private form ownership; ordinary shared dialogs retain their existing contract. */
internal interface NumberEntryRetention {
    val initial: TextFieldValue?
    fun allowsInput(): Boolean = true
    fun changed(value: TextFieldValue)
    fun closed()
    fun discarded()
}
internal val LocalNumberEntryRetention = staticCompositionLocalOf<NumberEntryRetention?> { null }
