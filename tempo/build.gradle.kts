/**
 * Tempo coach sources live under tempo/src/main/kotlin and are compiled into :app
 * (see app/build.gradle.kts sourceSets). This project exists so the module boundary
 * and JVM tests stay addressable without pulling Android into the coach tree.
 */
plugins {
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("tempoModulePresent") {
    group = "verification"
    description = "Marker task proving the :tempo module is wired."
    doLast {
        require(file("src/main/kotlin/com/sinura/personaltrainer/tempo/TempoEngine.kt").isFile)
    }
}
