package com.sinura.personaltrainer.insights

import com.sinura.personaltrainer.domain.DataHealth
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
    fun observeSharedHealth(includeWeekPlan: Boolean = true): Flow<DataHealth<TrainingInsights>>

    /** Collecting this flow requests a fresh shared read; cached replay cannot satisfy it. */
    fun retrySharedHealth(includeWeekPlan: Boolean = true): Flow<DataHealth<TrainingInsights>>

    fun observeShared(includeWeekPlan: Boolean = true): Flow<TrainingInsights>

    fun observe(
        window: Flow<HeatWindow> = flowOf(HeatWindow.CURRENT_WEEK),
        refresh: Flow<Any?> = flowOf(Unit),
        includeWeekPlan: Boolean = true,
    ): Flow<TrainingInsights>
}
