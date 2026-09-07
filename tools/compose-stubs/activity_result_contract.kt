// androidx.activity.result.contract — declaration-only. See compose-stubs/README.md.
// Only the four contracts this app registers are declared; a fifth is a visible RED.

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
