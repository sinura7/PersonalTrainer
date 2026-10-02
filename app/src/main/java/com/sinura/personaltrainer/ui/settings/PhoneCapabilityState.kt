package com.sinura.personaltrainer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sinura.personaltrainer.domain.PhoneCapabilityPort
import com.sinura.personaltrainer.domain.PhoneCapabilitySnapshot

@Composable
internal fun rememberPhoneCapabilitySnapshot(probe: PhoneCapabilityPort): PhoneCapabilitySnapshot {
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember(probe) { mutableStateOf(probe.read()) }
    DisposableEffect(lifecycleOwner, probe) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = probe.read()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return snapshot
}
