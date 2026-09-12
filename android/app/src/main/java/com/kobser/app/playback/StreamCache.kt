package com.kobser.app.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.kobser.app.data.repository.PreferencesRepository
import com.kobser.app.offline.OfflineManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device cache for streamed audio, so replaying a song doesn't re-download it.
 * Backed by Media3's [SimpleCache] (least-recently-used eviction at the size chosen
 * in Settings). Only one [SimpleCache] may be open per directory, hence a singleton.
 */
@UnstableApi
@Singleton
class StreamCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    private val offline: OfflineManager,
) {
    private val dir = File(context.cacheDir, "media")

    // The size limit is read once when the cache is first opened, so a new limit
    // picked in Settings applies the next time the app starts.
    private val cache: SimpleCache by lazy {
        val maxBytes = runBlocking { prefs.cacheMaxMb.first() } * 1024L * 1024L
        SimpleCache(dir, LeastRecentlyUsedCacheEvictor(maxBytes), StandaloneDatabaseProvider(context))
    }

    fun dataSourceFactory(): DataSource.Factory {
        // Previews only start flowing once the server has run yt-dlp for the video,
        // which can take well over ExoPlayer's 8 s default before the first byte.
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(45_000)
        val lru = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
            .setCacheKeyFactory { spec -> StreamCacheKeys.keyFor(spec.uri.toString()) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Pinned (offline) audio is checked first and read-only, so playing it
        // neither touches the network nor duplicates it into the LRU cache.
        return offline.readOnlyDataSourceFactory(lru)
    }

    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.keys.toList().forEach { cache.removeResource(it) }
    }
}
