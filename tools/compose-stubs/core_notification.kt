// androidx.core.app.NotificationManagerCompat — declaration-only. See compose-stubs/README.md.
// tools/compile-stubs/core.kt has NotificationCompat (the builder the notification code uses);
// this is the separate manager class, needed only by Compose sources.
//
// DELIBERATELY NARROWER: only from()/areNotificationsEnabled() are declared. notify(), cancel()
// and the channel APIs are omitted, so a new use is a visible RED.

package androidx.core.app

import android.content.Context

class NotificationManagerCompat private constructor() {
    fun areNotificationsEnabled(): Boolean = TODO("compile-only stub")

    companion object {
        @JvmStatic
        fun from(context: Context): NotificationManagerCompat = TODO("compile-only stub")
    }
}
