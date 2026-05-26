package com.kobser.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kobser.app.data.api.KobserApi
import com.kobser.app.data.api.StatsResponse
import com.kobser.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: KobserApi,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    var stats by mutableStateOf<StatsResponse?>(null)
    var statsLoading by mutableStateOf(true)
    var statsError by mutableStateOf<String?>(null)

    var rescanLoading by mutableStateOf(false)
    var rescanMessage by mutableStateOf<String?>(null)

    var searchSource by mutableStateOf("youtube_music")
        private set

    init {
        loadStats()
        viewModelScope.launch {
            prefs.searchSource.collect { searchSource = it }
        }
    }

    fun setSearchSource(source: String) {
        viewModelScope.launch { prefs.saveSearchSource(source) }
    }

    fun loadStats() {
        statsLoading = true
        statsError = null
        viewModelScope.launch {
            try {
                val r = api.getStats()
                if (r.isSuccessful) stats = r.body()
                else statsError = "Failed to load stats"
            } catch (e: Exception) {
                statsError = e.message
            } finally {
                statsLoading = false
            }
        }
    }

    fun rescan() {
        rescanLoading = true
        rescanMessage = null
        viewModelScope.launch {
            try {
                val r = api.rescan()
                rescanMessage = if (r.isSuccessful) "Library scan started" else "Scan failed"
            } catch (e: Exception) {
                rescanMessage = "Scan failed: ${e.message}"
            } finally {
                rescanLoading = false
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try { api.logout() } catch (_: Exception) {}
            prefs.clearSession()
            onDone()
        }
    }
}
