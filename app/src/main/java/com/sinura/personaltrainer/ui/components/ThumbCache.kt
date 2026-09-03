package com.sinura.personaltrainer.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One process-wide sampled-decode cache for catalog thumbs and the mark.
 *
 * `painterResource` decoded the full still on the main thread, per
 * composable instance. Catalog thumbs now ship at 256px; this still
 * samples on IO so a leftover large still cannot hitch Library scroll,
 * and so [TemperMark] does not decode a 768 heat still on first paint.
 */
object ThumbCache {
    /** 256px `ex_*` / pose pack: decode 1:1. */
    const val THUMB_SAMPLE = 1

    /**
     * 80dp mark on a 768 heat still. `inSampleSize` must be a power of
     * two; 2 yields 384px, enough at xxxhdpi without the 2.36 MB full
     * decode.
     */
    const val MARK_SAMPLE = 2

    internal var io: CoroutineDispatcher = Dispatchers.IO
    internal var decodeResource: (Resources, Int, BitmapFactory.Options) -> Bitmap? =
        { resources, id, options -> BitmapFactory.decodeResource(resources, id, options) }

    private val lock = Any()
    private val bitmaps = object : LruCache<Key, ImageBitmap>(MAX_KB) {
        override fun sizeOf(key: Key, value: ImageBitmap): Int =
            (value.width * value.height * 4) / 1024
    }

    fun peek(@DrawableRes id: Int, sample: Int): ImageBitmap? =
        synchronized(lock) { bitmaps.get(Key(id, sample)) }

    suspend fun load(
        resources: Resources,
        @DrawableRes id: Int,
        sample: Int,
    ): ImageBitmap? {
        peek(id, sample)?.let { return it }
        return withContext(io) {
            peek(id, sample)?.let { return@withContext it }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
                inSampleSize = sample.coerceAtLeast(1)
            }
            val decoded = decodeResource(resources, id, options) ?: return@withContext null
            val image = decoded.asImageBitmap()
            synchronized(lock) { bitmaps.put(Key(id, sample), image) }
            image
        }
    }

    internal fun clear() {
        synchronized(lock) { bitmaps.evictAll() }
        decodeResource = { resources, id, options ->
            BitmapFactory.decodeResource(resources, id, options)
        }
        io = Dispatchers.IO
    }

    private data class Key(@DrawableRes val id: Int, val sample: Int)

    /** ~32 thumbs at 256px ARGB, or a handful of mark/body bitmaps. */
    private const val MAX_KB = 8 * 1024
}
