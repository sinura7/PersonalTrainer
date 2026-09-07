// androidx.activity.result — declaration-only. See compose-stubs/README.md.
//
// The VARIANCE and the NULLABILITY here are the whole point of the file:
//
//  * ActivityResultContract<I, O> is invariant, as the real one is. Each contract below pins a
//    concrete I and O, so `CreateDocument` (I = String) cannot be handed the Array<String> that
//    `OpenDocument` wants, and the `onResult` lambda's parameter type is fixed by O.
//  * CreateDocument and OpenDocument produce `Uri?`. Both call sites in SettingsScreen.kt exist
//    only because of that null: `if (uri != null) ... else viewModel.cancelProtect()`. A non-null
//    Uri here would make those branches look dead.
//  * launch(input: I) is invariant in I, so `launcher.launch("name")` on an OpenDocument launcher
//    stays an error.

package androidx.activity.result

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/** A Java class in the real artifact, so Kotlin sees getResultCode()/getData() as the
 *  properties `resultCode: Int` and `data: Intent?`. `data` is nullable there and here. */
class ActivityResult(val resultCode: Int, val data: Intent?)

class IntentSenderRequest private constructor() {
    class Builder(intentSender: IntentSender) {
        fun setFillInIntent(fillInIntent: Intent?): Builder = TODO("compile-only stub")
        fun setFlags(values: Int, mask: Int): Builder = TODO("compile-only stub")
        fun build(): IntentSenderRequest = TODO("compile-only stub")
    }
}

abstract class ActivityResultLauncher<I> {
    abstract fun launch(input: I)
    abstract fun unregister()
}

/** What rememberLauncherForActivityResult returns. Real name, real supertype. */
abstract class ManagedActivityResultLauncher<I, O> : ActivityResultLauncher<I>()

interface ActivityResultCallback<O> {
    fun onActivityResult(result: O)
}
