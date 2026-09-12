package com.kobser.app.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.google.gson.Gson
import com.kobser.app.data.api.Song
import com.kobser.app.data.repository.LibraryRepository
import com.kobser.app.playback.StreamCacheKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** Download state of one pinned collection. */
data class OfflineProgress(val done: Int, val total: Int, val failed: Int, val running: Boolean) {
    val complete: Boolean get() = !running && done == total
}

/**
 * Keeps albums and playlists on the phone. Pinned audio lives in its own
 * never-evicted cache (separate from the play-through LRU cache in
 * [com.kobser.app.playback.StreamCache]), filled by Media3's [DownloadManager]
 * through [OfflineDownloadService], so downloads keep going in the background
 * and survive restarts. The list of what is pinned is a JSON catalog in app
 * storage so it can be browsed without the server.
 */
@UnstableApi
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val gson: Gson,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogFile = File(context.filesDir, "offline_catalog.json")

    // One database provider for everything Media3 stores in SQLite (both caches'
    // indexes and the download index share the file). Separate SQLiteOpenHelper
    // instances on the same file can fail with "database is locked".
    val databaseProvider: StandaloneDatabaseProvider by lazy { StandaloneDatabaseProvider(context) }

    val cache: SimpleCache by lazy {
        SimpleCache(File(context.filesDir, "offline_media"), NoOpCacheEvictor(), databaseProvider)
    }

    private val httpFactory: DataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(45_000)

    val downloadManager: DownloadManager by lazy {
        DownloadManager(context, databaseProvider, cache, httpFactory, Executors.newFixedThreadPool(2)).apply {
            maxParallelDownloads = 2
            minRetryCount = 2
            requirements = Requirements(Requirements.NETWORK)
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) = scheduleProgress()
                override fun onDownloadRemoved(dm: DownloadManager, download: Download) = scheduleProgress()
                override fun onIdle(dm: DownloadManager) = scheduleProgress()
                override fun onInitialized(dm: DownloadManager) = scheduleProgress()
            })
        }
    }

    private val _catalog = MutableStateFlow(OfflineCatalog())
    val catalog: StateFlow<OfflineCatalog> = _catalog.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, OfflineProgress>>(emptyMap())
    val progress: StateFlow<Map<String, OfflineProgress>> = _progress.asStateFlow()

    private var progressJob: Job? = null

    init {
        scope.launch {
            _catalog.value = OfflineCatalog.fromJson(runCatching { catalogFile.readText() }.getOrNull(), gson)
            if (_catalog.value.collections.isNotEmpty()) {
                // Touch the manager so persisted downloads resume, and keep them
                // going in the foreground service if any are pending.
                downloadManager
                // May be created from a background context (e.g. the playback service
                // started by Android Auto), where starting a service is refused; the
                // in-process manager still downloads, the service just adds the notification.
                withContext(Dispatchers.Main) {
                    runCatching { DownloadService.start(context, OfflineDownloadService::class.java) }
                }
                scheduleProgress()
            }
        }
    }

    /** A read-only data source over the offline cache, to sit in front of the playback chain. */
    fun readOnlyDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { StreamCacheKeys.keyFor(it.uri.toString()) }
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun isPinned(key: String): Boolean = _catalog.value.find(key) != null

    private fun cacheKey(songId: String) = StreamCacheKeys.keyFor(repository.getStreamUrl(songId))

    /** Disk-backed check — call off the main thread. */
    fun isSongCached(songId: String): Boolean = isKeyCached(cacheKey(songId))

    private fun isKeyCached(key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.getCachedBytes(key, 0, length) == length
    }

    suspend fun cachedSongIds(songs: List<Song>): Set<String> = withContext(Dispatchers.IO) {
        songs.map { it.id }.filter { isSongCached(it) }.toSet()
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    fun pin(collection: OfflineCollection) {
        _catalog.update { it.upsert(collection) }
        persist()
        _progress.update { it + (collection.key to OfflineProgress(0, collection.songs.size, 0, running = true)) }
        collection.songs.forEach { song ->
            val url = repository.getStreamUrl(song.id)
            val request = DownloadRequest.Builder(cacheKey(song.id), Uri.parse(url))
                .setCustomCacheKey(cacheKey(song.id))
                .build()
            // Download ids are the cache keys, so a song shared by two pinned
            // collections is fetched once.
            DownloadService.sendAddDownload(context, OfflineDownloadService::class.java, request, /* foreground= */ true)
        }
        scheduleProgress()
    }

    fun unpin(key: String) {
        val evictable = _catalog.value.songsOnlyIn(key)
        _catalog.update { it.remove(key) }
        _progress.update { it - key }
        persist()
        // Removing a download also deletes its bytes from the cache.
        evictable.forEach { id ->
            DownloadService.sendRemoveDownload(context, OfflineDownloadService::class.java, cacheKey(id), /* foreground= */ false)
        }
    }

    suspend fun removeAll() {
        _catalog.value = OfflineCatalog()
        _progress.value = emptyMap()
        persist()
        DownloadService.sendRemoveAllDownloads(context, OfflineDownloadService::class.java, /* foreground= */ false)
        withContext(Dispatchers.IO) { cache.keys.toList().forEach { runCatching { cache.removeResource(it) } } }
    }

    /** Recomputes per-collection progress from the download index; coalesces bursts of events. */
    private fun scheduleProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            delay(250)
            val states = HashMap<String, Int>()
            runCatching {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val d = cursor.download
                        states[d.request.id] = d.state
                    }
                }
            }
            val catalog = _catalog.value
            val cachedKeys = catalog.allSongs().map { cacheKey(it.id) }.filter { isKeyCached(it) }.toSet()
            _progress.value = catalog.collections.associate { c ->
                c.key to collectionProgress(c.songs.map { cacheKey(it.id) }, states, cachedKeys)
            }
        }
    }

    private fun persist() {
        val json = _catalog.value.toJson(gson)
        scope.launch { runCatching { catalogFile.writeText(json) } }
    }
}
