package com.sinura.personaltrainer.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun openLegalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
