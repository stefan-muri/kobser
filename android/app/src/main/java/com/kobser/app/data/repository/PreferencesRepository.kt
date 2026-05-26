package com.kobser.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SESSION_ID = stringPreferencesKey("session_id")
        val LAST_TRACK_JSON = stringPreferencesKey("last_track_json")
        val SEARCH_SOURCE = stringPreferencesKey("search_source")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val sessionId: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_ID] }
    val lastTrackJson: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_TRACK_JSON] }
    val searchSource: Flow<String> = context.dataStore.data.map { it[Keys.SEARCH_SOURCE] ?: "youtube_music" }

    @Volatile var cachedServerUrl: String = ""
        private set
    @Volatile var cachedSessionId: String = ""
        private set

    init {
        scope.launch {
            serverUrl.collect { cachedServerUrl = it ?: "" }
        }
        scope.launch {
            sessionId.collect { cachedSessionId = it ?: "" }
        }
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun saveSessionId(id: String) {
        context.dataStore.edit { it[Keys.SESSION_ID] = id }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.remove(Keys.SESSION_ID) }
    }

    suspend fun saveLastTrack(json: String) {
        context.dataStore.edit { it[Keys.LAST_TRACK_JSON] = json }
    }

    suspend fun saveSearchSource(source: String) {
        context.dataStore.edit { it[Keys.SEARCH_SOURCE] = source }
    }

    suspend fun clearLastTrack() {
        context.dataStore.edit { it.remove(Keys.LAST_TRACK_JSON) }
    }
}
