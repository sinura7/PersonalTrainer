package com.sinura.personaltrainer.domain

/**
 * Packet G: words on the set-row overflow, never a glyph alone.
 *
 * Each logged row carries a visible 48 dp overflow. The menu names the set it acts on so a
 * TalkBack pass hears which row is armed, and Delete is a word in Danger — never red alone.
 */
object SetRowCopy {
    fun actionsForSet(setNumber: Int): String = "Actions for set $setNumber"

    fun reviseSet(setNumber: Int): String = "Revise set $setNumber"

    fun deleteSet(setNumber: Int): String = "Delete set $setNumber"
}
