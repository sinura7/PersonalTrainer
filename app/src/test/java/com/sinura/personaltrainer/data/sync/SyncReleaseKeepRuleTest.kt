package com.sinura.personaltrainer.data.sync

import com.google.gson.annotations.SerializedName
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release builds run R8. Sync rows reach the server through Gson by reflection, so a field R8
 * renames is a JSON key the server has never heard of. The debug build is not minified, which
 * is why nothing on the phone would ever show this.
 */
class SyncReleaseKeepRuleTest {
    @Test
    fun releaseMinifyKeepsTheSyncPackageWhole() {
        val rules = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
            .first { it.isFile }
            .readLines()
            .map { it.trim() }
        assertTrue(rules.contains("-keep class com.sinura.personaltrainer.data.sync.** { *; }"))
    }

    @Test
    fun syncRowsDependOnFieldNamesTheRuleProtects() {
        // If every field carried @SerializedName the rule would be belt and braces. They do not.
        val bare = RemoteActivitySessionRow::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { it.getAnnotation(SerializedName::class.java) == null }
            .map { it.name }
        assertTrue(bare.containsAll(listOf("id", "status", "title", "notes")))
    }
}
