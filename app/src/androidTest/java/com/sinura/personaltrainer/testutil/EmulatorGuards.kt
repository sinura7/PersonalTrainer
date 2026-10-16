package com.sinura.personaltrainer.testutil

import android.os.Build

/** Ranchu/goldfish QEMU hardware — never a physical device. */
internal fun isQemuEmulatorHardware(): Boolean =
    Build.HARDWARE in setOf("ranchu", "goldfish")

/**
 * AVD images from the SDK manager use [Android/sdk_…]; Play-system images on newer API
 * levels (including GitHub's android-emulator-runner Pixel profiles) use [google/sdk_…].
 */
internal fun isSdkAvdFingerprint(): Boolean =
    Build.FINGERPRINT.startsWith("Android/sdk_") || Build.FINGERPRINT.startsWith("google/sdk_")

internal fun isDisposableSdkEmulator(): Boolean =
    isQemuEmulatorHardware() && isSdkAvdFingerprint()
