// androidx.activity.result.contract — declaration-only. See compose-stubs/README.md.
// Only the contracts this app registers are declared; one it does not is a visible RED.
// StartActivityForResult was added when trunk's #188 (the lock-screen challenge before the
// backup password) reached this lane — the missing declaration is exactly the RED the rule
// above is meant to produce, rather than a silent pass.

package androidx.activity.result.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest

abstract class ActivityResultContract<I, O> {
    abstract fun createIntent(context: Context, input: I): Intent
    abstract fun parseResult(resultCode: Int, intent: Intent?): O
}

class ActivityResultContracts private constructor() {
    /** I = the IntentSender request, O = the raw ActivityResult (resultCode + data). */
    class StartIntentSenderForResult : ActivityResultContract<IntentSenderRequest, ActivityResult>() {
        override fun createIntent(context: Context, input: IntentSenderRequest): Intent =
            TODO("compile-only stub")
        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult =
            TODO("compile-only stub")
    }

    /**
     * I = the Intent to launch, O = the raw ActivityResult (resultCode + data).
     *
     * The real contract is `ActivityResultContract<Intent, ActivityResult>`; the input is the
     * Intent itself, NOT an IntentSenderRequest, which is what separates it from
     * [StartIntentSenderForResult] above. Declaring it with the wrong input type would let a
     * swapped pair of launchers compile here and fail in the merge gate.
     */
    class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>() {
        override fun createIntent(context: Context, input: Intent): Intent = TODO("compile-only stub")
        override fun parseResult(resultCode: Int, intent: Intent?): ActivityResult =
            TODO("compile-only stub")
    }

    /** I = the suggested file name, O = the created document's Uri, null if the user backed out. */
    open class CreateDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent = TODO("compile-only stub")
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = TODO("compile-only stub")
    }

    /** I = the acceptable MIME types, O = the chosen document's Uri, null if cancelled. */
    open class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            TODO("compile-only stub")
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = TODO("compile-only stub")
    }

    /** I = the permission string, O = granted. */
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String): Intent = TODO("compile-only stub")
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = TODO("compile-only stub")
    }
}
