package com.sinura.personaltrainer.data.sync

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal val syncGson: Gson = GsonBuilder().create()

internal inline fun <reified T> encodeSync(value: T): String = syncGson.toJson(value)

internal inline fun <reified T> decodeSync(json: String): T = syncGson.fromJson(json, T::class.java)
