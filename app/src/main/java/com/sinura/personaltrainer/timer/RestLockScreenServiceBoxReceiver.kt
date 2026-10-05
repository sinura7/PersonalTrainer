package com.sinura.personaltrainer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Answers Samsung SystemUI [REQUEST_SERVICEBOX_REMOTEVIEWS] for lock/AOD surfaces.
 * Same RemoteViews as the monotone AppWidget providers (blur-widget-demo optional path).
 */
class RestLockScreenServiceBoxReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REQUEST_SERVICEBOX_REMOTEVIEWS) return
        val requestedPageId = intent.getStringExtra(EXTRA_PAGE_ID)
        val pageIds = when {
            requestedPageId == null -> PAGE_IDS
            requestedPageId in PAGE_IDS -> listOf(requestedPageId)
            else -> return
        }
        val snapshot = restSnapshot(context) ?: RestTimerSnapshot(running = false)
        pageIds.forEach { pageId -> sendResponse(context, pageId, snapshot) }
    }

    private fun restSnapshot(context: Context): RestTimerSnapshot? =
        (context.applicationContext as? PersonalTrainerApp)
            ?.container
            ?.restTimerController
            ?.snapshot
            ?.value

    private fun sendResponse(context: Context, pageId: String, snapshot: RestTimerSnapshot) {
        val views = RestLockScreenWidgetViews.serviceBoxViews(context, pageId, snapshot)
        context.sendBroadcast(
            Intent(ACTION_RESPONSE_SERVICEBOX_REMOTEVIEWS).apply {
                setPackage(SYSTEMUI_PACKAGE)
                putExtra(EXTRA_PACKAGE, context.packageName)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_SHOW, true)
                putExtra(EXTRA_ORIGIN, views)
                putExtra(EXTRA_AOD, views)
            },
        )
    }

    companion object {
        const val PAGE_REST_1X1 = "temper_rest_countdown_1x1"
        const val PAGE_REST_2X1 = "temper_rest_countdown_2x1"

        private const val ACTION_REQUEST_SERVICEBOX_REMOTEVIEWS =
            "com.samsung.android.intent.action.REQUEST_SERVICEBOX_REMOTEVIEWS"
        private const val ACTION_RESPONSE_SERVICEBOX_REMOTEVIEWS =
            "com.samsung.android.intent.action.RESPONSE_SERVICEBOX_REMOTEVIEWS"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_PAGE_ID = "pageId"
        private const val EXTRA_SHOW = "show"
        private const val EXTRA_ORIGIN = "origin"
        private const val EXTRA_AOD = "aod"

        private val PAGE_IDS = listOf(PAGE_REST_1X1, PAGE_REST_2X1)
    }
}
