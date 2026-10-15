package com.sinura.personaltrainer.data.sync

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal val syncGson: Gson = GsonBuilder().create()
internal val syncGsonKeepingNulls: Gson = GsonBuilder().serializeNulls().create()

internal inline fun <reified T> encodeSync(value: T): String = syncGson.toJson(value)

/** Keeps a null deleted_at so a later weigh-in on that day clears the tombstone. */
internal inline fun <reified T> encodeSyncKeepingNulls(value: T): String = syncGsonKeepingNulls.toJson(value)

internal inline fun <reified T> decodeSync(json: String): T = syncGson.fromJson(json, T::class.java)
