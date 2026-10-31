package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

/** v4 blueprint V4: rest is inline on the workout. No destination is a rest page. */
class NavGraphTest {
    private fun routePaths(): Map<String, String> = Route::class.java.declaredClasses
        .filter { Route::class.java.isAssignableFrom(it) }
        .mapNotNull { type ->
            val instance = runCatching { type.getField("INSTANCE").get(null) }.getOrNull() as? Route
            instance?.let { type.simpleName to it.path }
        }
        .toMap()

    @Test
    fun noRestRoute() {
        val paths = routePaths()
        assertTrue("routes were read: $paths", paths.containsKey("ActiveWorkout"))
        assertTrue("no RestTimer route: ${paths.keys}", "RestTimer" !in paths.keys)
        val restPaths = paths.values.filter { path -> path.split('/', '?').any { it == "rest" } }
        assertTrue("no rest page path: $restPaths", restPaths.isEmpty())
    }
}
