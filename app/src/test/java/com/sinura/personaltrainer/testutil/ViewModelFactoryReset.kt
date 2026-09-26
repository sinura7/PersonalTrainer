package com.sinura.personaltrainer.testutil

import androidx.lifecycle.ViewModelProvider

/**
 * androidx keeps the first Application it is handed in a process-wide default ViewModel factory,
 * and builds every later `viewModel()` AndroidViewModel with it. A phone has one Application per
 * process; a JVM test process makes one per test. So a test that mounts the whole app after
 * another test did got ViewModels over the earlier test's container and database, and waited on
 * screens that never came. Called before such a test. If a lifecycle upgrade renames the field,
 * this throws rather than letting those tests pass on the wrong app.
 */
fun forgetFirstApplication() {
    ViewModelProvider.AndroidViewModelFactory::class.java
        .getDeclaredField("_instance")
        .apply { isAccessible = true }
        .set(null, null)
}
