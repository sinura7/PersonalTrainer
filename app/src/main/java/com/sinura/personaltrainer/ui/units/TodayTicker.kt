package com.sinura.personaltrainer.ui.units

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.todayEpochDay
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.delay

/**
 * Civil today for composition. ViewModels keep [TimePort]; screens that
 * asked `todayEpochDay()` or `LocalDate.now()` at composition time used
 * to freeze the day until process death.
 *
 * Re-reads on `ON_RESUME` and at the next local midnight.
 */
val LocalTodayEpochDay = staticCompositionLocalOf {
    todayEpochDay(nowMs = JvmTime.nowMillis(), time = JvmTime)
}

fun millisUntilNextLocalMidnight(
    nowMs: Long,
    time: TimePort,
    zoneId: String = time.defaultZoneId(),
): Long {
    val today = time.civilDate(nowMs, zoneId)
    val nextMidnight = time.startOfDayMillis(today.plusDays(1), zoneId)
    return (nextMidnight - nowMs).coerceAtLeast(1L)
}

@Composable
fun rememberTodayEpochDay(time: TimePort = JvmTime): Long {
    var today by remember(time) {
        mutableLongStateOf(todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = time.defaultZoneId()))
    }
    var resumeGeneration by remember(time) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, time) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = time.defaultZoneId())
                // The same civil date can now have a different next-midnight deadline.
                // Reschedule even when rereading today did not change its state value.
                resumeGeneration += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(today, time, resumeGeneration) {
        while (true) {
            delay(millisUntilNextLocalMidnight(time.nowMillis(), time))
            today = todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = time.defaultZoneId())
        }
    }
    return today
}
