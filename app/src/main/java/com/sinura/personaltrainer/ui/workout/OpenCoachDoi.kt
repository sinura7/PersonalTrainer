package com.sinura.personaltrainer.ui.workout

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sinura.personaltrainer.domain.coach.TempoWhySheetCopy

internal fun openCoachDoi(context: Context, doi: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TempoWhySheetCopy.doiUrl(doi)))
    runCatching { context.startActivity(intent) }
}
