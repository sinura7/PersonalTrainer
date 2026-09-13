package com.sinura.personaltrainer.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val Context.debugUpdateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "debug_update",
)

private val CHECKED_AT = longPreferencesKey("checked_at")
private val OFFER_VERSION = intPreferencesKey("offer_version")
private val OFFER_TAG = stringPreferencesKey("offer_tag")
private val OFFER_RELEASE_URL = stringPreferencesKey("offer_release_url")
private val OFFER_APK_URL = stringPreferencesKey("offer_apk_url")
private val DISMISSED_VERSION = intPreferencesKey("dismissed_version")

internal class DataStoreDebugUpdateCache(
    private val store: DataStore<Preferences>,
) : DebugUpdateCache {
    override suspend fun load(): CachedDebugCheck? {
        val prefs = store.data.first()
        val checkedAt = prefs[CHECKED_AT] ?: return null
        val version = prefs[OFFER_VERSION] ?: 0
        val offer = if (version > 0) {
            val tag = prefs[OFFER_TAG] ?: return CachedDebugCheck(checkedAt, null)
            val releaseUrl = prefs[OFFER_RELEASE_URL] ?: return CachedDebugCheck(checkedAt, null)
            val apkUrl = prefs[OFFER_APK_URL] ?: return CachedDebugCheck(checkedAt, null)
            DebugUpdateOffer(
                versionCode = version,
                tag = tag,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
            )
        } else {
            null
        }
        return CachedDebugCheck(checkedAtMillis = checkedAt, offer = offer)
    }

    override suspend fun save(check: CachedDebugCheck) {
        store.edit { prefs ->
            prefs[CHECKED_AT] = check.checkedAtMillis
            val offer = check.offer
            if (offer == null) {
                prefs.remove(OFFER_VERSION)
                prefs.remove(OFFER_TAG)
                prefs.remove(OFFER_RELEASE_URL)
                prefs.remove(OFFER_APK_URL)
            } else {
                prefs[OFFER_VERSION] = offer.versionCode
                prefs[OFFER_TAG] = offer.tag
                prefs[OFFER_RELEASE_URL] = offer.releaseUrl
                prefs[OFFER_APK_URL] = offer.apkUrl
            }
        }
    }

    override suspend fun dismissedVersionCode(): Int =
        store.data.first()[DISMISSED_VERSION] ?: 0

    override suspend fun dismiss(versionCode: Int) {
        store.edit { prefs -> prefs[DISMISSED_VERSION] = versionCode }
    }
}
