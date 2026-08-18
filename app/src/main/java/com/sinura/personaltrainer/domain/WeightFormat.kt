package com.sinura.personaltrainer.domain

fun Double.toKgLabel(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toInt()} kg"
    } else {
        "${rounded} kg"
    }
}

fun Double.toKgNumber(): String {
    val rounded = kotlin.math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
