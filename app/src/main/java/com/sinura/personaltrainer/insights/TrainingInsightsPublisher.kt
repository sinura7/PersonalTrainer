package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingInsights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The analytics stream ViewModels actually collect.
 *
 * Production is [TrainingInsightsSource]. Tests pass a fake that emits a controlled
 * [TrainingInsights] so Home / Plan / setup can be asserted without re-running the
 * calculator those domain tests already cover.
 */
interface TrainingInsightsPublisher {
    fun observeShared(includeWeekPlan: Boolean = true): Flow<TrainingInsights>

    fun observe(
        window: Flow<HeatWindow> = flowOf(HeatWindow.CURRENT_WEEK),
        refresh: Flow<Any?> = flowOf(Unit),
        includeWeekPlan: Boolean = true,
    ): Flow<TrainingInsights>
}
