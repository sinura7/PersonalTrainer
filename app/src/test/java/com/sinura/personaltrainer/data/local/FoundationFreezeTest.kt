package com.sinura.personaltrainer.data.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationFreezeTest {
    @Test
    fun foundationGenerationIsFrozenAndLegacyIsNotProduction() {
        assertTrue(FoundationGeneration.FROZEN)
        assertTrue(FoundationGeneration.DATABASE_FILE == "temper.db")
        assertTrue(FoundationGeneration.LEGACY_DATABASE_FILE == "personal_trainer.db")
        val container = source("app/src/main/java/com/sinura/personaltrainer/AppContainer.kt")
        val temper = source("app/src/main/java/com/sinura/personaltrainer/data/local/TemperDatabase.kt")
        val trainer = source("app/src/main/java/com/sinura/personaltrainer/data/local/TrainerDatabase.kt")
        assertTrue(container.readText().contains("TemperDatabase.create"))
        assertFalse(container.readText().contains("TrainerDatabase.create"))
        assertFalse(temper.readText().contains(".fallbackToDestructiveMigration"))
        assertFalse(trainer.readText().contains(".fallbackToDestructiveMigration"))
        assertTrue(trainer.readText().contains("personal_trainer.db"))
        val schema = source("app/schemas/com.sinura.personaltrainer.data.local.TemperDatabase/1.json")
        assertTrue(schema.readText().contains("\"identityHash\": \"a07cac89e03d333eff3c9566d8c5a637\""))
        assertTrue(schema.readText().contains("activity_sessions"))
        val upgrade = source(
            "app/src/test/java/com/sinura/personaltrainer/data/local/UpgradeInPlaceTest.kt",
        ).readText()
        assertTrue(upgrade.contains("TemperDatabase.create"))
        assertFalse(upgrade.contains("TrainerDatabase.create"))
        val gradle = source("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationIdSuffix = \".debug\""))
        assertFalse(gradle.contains("applicationIdSuffix = \".debug.\$debugLiveCode\""))
    }

    private fun source(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File(relative.removePrefix("app/")),
        )
        return candidates.first { it.isFile }
    }
}
