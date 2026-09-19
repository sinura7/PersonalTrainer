package com.sinura.personaltrainer.ui.units

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val zoneId = time.defaultZoneId()
    var today by remember {
        mutableLongStateOf(todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = zoneId))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, time) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = zoneId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(today, time) {
        while (true) {
            delay(millisUntilNextLocalMidnight(time.nowMillis(), time, zoneId))
            today = todayEpochDay(nowMs = time.nowMillis(), time = time, zoneId = zoneId)
        }
    }
    return today
}
